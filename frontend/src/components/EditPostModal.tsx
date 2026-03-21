import React, { useEffect, useState } from 'react';
import { Grid } from '@giphy/react-components';
import type { GifsResult } from '@giphy/js-fetch-api';
import type { Post } from '../types/post';
import RichTextEditor from './RichTextEditor';
import { fetchWithCorrelation, getRequestId } from '../api/fetchWithCorrelation';
import './AddPostModal.css';

interface GiphyGif {
    id: string;
    images: {
        downsized_medium?: { url: string };
        fixed_height?: { url: string };
    };
}

interface EditPostModalProps {
    boardId: string;
    post: Post;
    accessToken?: string | null;
    capabilityToken?: string | null;
    onClose: () => void;
    onPostUpdated: (updated: Post) => void;
}

export default function EditPostModal({ boardId, post, accessToken, capabilityToken, onClose, onPostUpdated }: EditPostModalProps) {
    const [authorName, setAuthorName] = useState(post.authorName ?? '');
    const [messageText, setMessageText] = useState(post.messageText ?? '');
    const [selectedGif, setSelectedGif] = useState<GiphyGif | null>(null);
    const [uploadedImageUrl, setUploadedImageUrl] = useState<string | null>(post.uploadedImageUrl ?? null);
    const [isUploadingImage, setIsUploadingImage] = useState(false);

    const [showGifSearch, setShowGifSearch] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [giphyUnconfigured, setGiphyUnconfigured] = useState(false);

    useEffect(() => {
        if (post.giphyUrl) {
            setSelectedGif({
                id: 'existing',
                images: { downsized_medium: { url: post.giphyUrl } },
            });
        }
    }, [post.giphyUrl]);

    const emptyGifResult = (status = 200, message = 'OK'): GifsResult => ({
        data: [],
        pagination: { total_count: 0, count: 0, offset: 0 },
        meta: { status, msg: message, response_id: '' },
    });

    const fetchGifs = async (offset: number): Promise<GifsResult> => {
        const params = new URLSearchParams({ limit: '10', offset: String(offset) });
        if (searchQuery.trim()) params.set('q', searchQuery.trim());
        const url = searchQuery.trim()
            ? `/api/giphy/search?${params}`
            : `/api/giphy/trending?${params}`;
        const res = await fetchWithCorrelation(url, { headers: { 'Accept': 'application/json; version=1' } });
        if (res.status === 503) {
            setGiphyUnconfigured(true);
            return emptyGifResult(503, 'GIF search unavailable');
        }
        const json = await res.json();
        if (json.error) {
            setGiphyUnconfigured(true);
            return emptyGifResult(503, 'GIF search unavailable');
        }
        return {
            data: (json.data ?? []) as GifsResult['data'],
            pagination: json.pagination ?? { total_count: 0, count: 0, offset: 0 },
            meta: json.meta ?? { status: 200, msg: 'OK', response_id: '' },
        };
    };

    const maxImageBytes = 5 * 1024 * 1024;

    const uploadSelectedImage = async (file: File) => {
        setIsUploadingImage(true);
        setSubmitError(null);
        try {
            const presignRes = await fetchWithCorrelation(`/api/boards/${boardId}/images/presign`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json; version=1' },
                body: JSON.stringify({ contentType: file.type, contentLengthBytes: file.size }),
            });
            if (!presignRes.ok) {
                const rid = getRequestId(presignRes);
                let errorMessage = 'Failed to start image upload.';
                try {
                    const payload = await presignRes.json();
                    if (payload?.error && typeof payload.error === 'string') errorMessage = payload.error;
                } catch {
                    // ignore
                }
                if (rid) errorMessage += ` (ref: ${rid})`;
                throw new Error(errorMessage);
            }
            const presign = await presignRes.json();
            const uploadUrl = presign.uploadUrl as string;
            const imageUrl = presign.imageUrl as string;

            const putRes = await fetch(uploadUrl, {
                method: 'PUT',
                headers: {
                    'Content-Type': file.type,
                    'Accept': 'application/json; version=1',
                },
                body: file,
            });
            if (!putRes.ok) {
                throw new Error('Image upload failed.');
            }

            setUploadedImageUrl(imageUrl);
            setSelectedGif(null);
        } finally {
            setIsUploadingImage(false);
        }
    };

    const handleFileSelected = async (file: File | null) => {
        if (!file) return;

        if (!['image/png', 'image/jpeg'].includes(file.type)) {
            setSubmitError('Unsupported image type. Please select a PNG or JPEG.');
            return;
        }
        if (file.size > maxImageBytes) {
            setSubmitError('Image is too large. Max size is 5MB.');
            return;
        }

        await uploadSelectedImage(file);
    };

    const handleGifSelect = (gif: unknown) => {
        setSelectedGif(gif as GiphyGif);
        setUploadedImageUrl(null);
        setShowGifSearch(false);
    };

    const resolveGifUrl = (gif: GiphyGif | null): string | null => {
        if (!gif) return null;
        return gif.images?.downsized_medium?.url || gif.images?.fixed_height?.url || null;
    };

    const handleSubmit = async () => {
        setIsSubmitting(true);
        setSubmitError(null);
        try {
            const giphyUrl = resolveGifUrl(selectedGif);
            const plainText = messageText?.trim() ?? '';

            if (!authorName.trim()) {
                setSubmitError('Please enter your name.');
                return;
            }
            if (!plainText && !giphyUrl && !uploadedImageUrl) {
                setSubmitError('Please add a message, GIF, or image.');
                return;
            }

            const res = await fetchWithCorrelation(`/api/boards/${boardId}/posts/${post.id}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    Accept: 'application/json; version=1',
                    ...(capabilityToken
                        ? { 'X-Post-Capability-Token': capabilityToken }
                        : accessToken
                            ? { Authorization: `Bearer ${accessToken}` }
                            : {}),
                },
                body: JSON.stringify({
                    authorName,
                    messageText: plainText || null,
                    giphyUrl,
                    uploadedImageUrl,
                }),
            });

            if (!res.ok) {
                if (res.status === 403) {
                    throw new Error('You can only edit posts on boards you own.');
                }
                if (res.status === 404) {
                    throw new Error('Post not found.');
                }
                const rid = getRequestId(res);
                let errorMessage = 'Failed to update post.';
                try {
                    const payload = await res.json();
                    if (payload?.error && typeof payload.error === 'string') errorMessage = payload.error;
                } catch {
                    // ignore
                }
                if (rid) errorMessage += ` (ref: ${rid})`;
                throw new Error(errorMessage);
            }

            const updated = (await res.json()) as Post;
            onPostUpdated(updated);
            onClose();
        } catch (err) {
            setSubmitError(err instanceof Error ? err.message : 'Failed to update post.');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content add-post-modal" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>Edit post</h2>
                    <button className="close-btn" onClick={onClose} aria-label="Close edit post modal">
                        &times;
                    </button>
                </div>

                <form
                    className="add-post-form"
                    onSubmit={(e) => {
                        e.preventDefault();
                        handleSubmit();
                    }}
                >
                    <div className="form-group row-group">
                        <button
                            type="button"
                            className={`media-btn ${showGifSearch ? 'active' : ''} ${isUploadingImage ? 'disabled' : ''}`}
                            onClick={() => {
                                if (isUploadingImage) return;
                                if (uploadedImageUrl) setUploadedImageUrl(null);
                                setShowGifSearch(!showGifSearch);
                            }}
                            aria-disabled={isUploadingImage}
                        >
                            🎞️ {selectedGif ? 'Change GIF' : 'Add a GIF'}
                        </button>

                        <label
                            className={`media-btn ${isUploadingImage ? 'disabled' : ''}`}
                            style={{ display: 'inline-block' }}
                            aria-disabled={isUploadingImage}
                        >
                            🖼️ {uploadedImageUrl ? 'Change image' : 'Upload image'}
                            <input
                                type="file"
                                accept="image/png,image/jpeg"
                                onChange={(e) => handleFileSelected(e.target.files?.[0] ?? null)}
                                disabled={isUploadingImage}
                                data-testid="upload-image-input"
                                style={{ display: 'none' }}
                                id="edit-upload-input"
                            />
                        </label>
                    </div>

                    {showGifSearch && (
                        <div className="gif-search-container">
                            {giphyUnconfigured ? (
                                <p className="gif-search-hint">
                                    GIF search is not configured. Set <code>GIPHY_API_KEY</code> (env) or <code>giphy.api-key</code> in the backend (e.g. <code>application-secret.properties</code>).
                                </p>
                            ) : (
                                <>
                                    <input
                                        type="text"
                                        placeholder="Search Giphy..."
                                        className="gif-search-input"
                                        value={searchQuery}
                                        onChange={(e) => setSearchQuery(e.target.value)}
                                    />
                                    <div className="gif-grid-wrapper">
                                        <Grid
                                            width={620}
                                            columns={3}
                                            fetchGifs={fetchGifs}
                                            key={searchQuery}
                                            onGifClick={(gif: unknown, e: React.MouseEvent<HTMLElement>) => {
                                                e.preventDefault();
                                                handleGifSelect(gif);
                                            }}
                                        />
                                    </div>
                                </>
                            )}
                        </div>
                    )}

                    {selectedGif && (
                        <div className="selected-gif-preview">
                            <img src={resolveGifUrl(selectedGif) ?? ''} alt="Selected GIF" />
                            <button type="button" onClick={() => setSelectedGif(null)}>
                                Remove
                            </button>
                        </div>
                    )}

                    {uploadedImageUrl && (
                        <div className="selected-gif-preview">
                            <img src={uploadedImageUrl} alt="Selected upload" />
                            <button type="button" onClick={() => setUploadedImageUrl(null)}>
                                Remove
                            </button>
                        </div>
                    )}

                    {isUploadingImage && !uploadedImageUrl && (
                        <div className="uploading-preview" aria-live="polite">
                            <span className="spinner" aria-hidden="true" />
                            <span>Uploading image…</span>
                        </div>
                    )}

                    <div className="form-group main-message-group">
                        <RichTextEditor
                            value={messageText}
                            onChange={setMessageText}
                            placeholder="Add a message..."
                        />
                    </div>

                    <div className="form-footer">
                        <div className="author-input">
                            <span className="from-label">From:</span>
                            <input
                                type="text"
                                placeholder="Your Name"
                                value={authorName}
                                onChange={(e) => setAuthorName(e.target.value)}
                                required
                            />
                        </div>
                        {submitError && <p className="error-text">{submitError}</p>}
                        <button
                            type="submit"
                            className="btn-primary"
                            disabled={isSubmitting || isUploadingImage}
                            data-testid="save-post-button"
                        >
                            {isSubmitting ? 'Saving…' : 'Save'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

