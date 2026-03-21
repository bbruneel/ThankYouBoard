import React, { useState } from 'react';
import { Grid } from '@giphy/react-components';
import type { GifsResult } from '@giphy/js-fetch-api';
import type { Post } from '../types/post';
import RichTextEditor from './RichTextEditor';
import { fetchWithCorrelation, getRequestId } from '../api/fetchWithCorrelation';
import { setPostCapabilityToken } from '../utils/postCapabilityTokens';
import './AddPostModal.css';

interface GiphyGif {
    id: string;
    images: {
        downsized_medium?: { url: string };
        fixed_height?: { url: string };
    };
}

interface AddPostModalProps {
    boardId: string;
    onClose: () => void;
    onPostAdded: (newPost: Post) => void;
}

export default function AddPostModal({ boardId, onClose, onPostAdded }: AddPostModalProps) {
    const [authorName, setAuthorName] = useState('');
    const [messageText, setMessageText] = useState('');
    const [selectedGif, setSelectedGif] = useState<GiphyGif | null>(null);
    const [uploadedImageUrl, setUploadedImageUrl] = useState<string | null>(null);
    const [isUploadingImage, setIsUploadingImage] = useState(false);

    const [showGifSearch, setShowGifSearch] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [giphyUnconfigured, setGiphyUnconfigured] = useState(false);

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
        } finally {
            setIsUploadingImage(false);
        }
    };

    const handleFileSelected = async (file: File | null) => {
        setUploadedImageUrl(null);
        if (!file) return;

        if (!['image/png', 'image/jpeg'].includes(file.type)) {
            setSubmitError('Unsupported image type. Please select a PNG or JPEG.');
            return;
        }
        if (file.size > maxImageBytes) {
            setSubmitError('Image is too large. Max size is 5MB.');
            return;
        }

        // Enforce: either GIF or upload, not both.
        if (selectedGif) setSelectedGif(null);
        if (showGifSearch) setShowGifSearch(false);

        try {
            await uploadSelectedImage(file);
        } catch (e) {
            const msg = e instanceof Error ? e.message : 'Failed to upload image.';
            setSubmitError(msg);
            setUploadedImageUrl(null);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        const plainText = messageText.replace(/<[^>]*>/g, '').trim();
        if (selectedGif && uploadedImageUrl) {
            setSubmitError('Please choose either a GIF or an uploaded image (not both).');
            return;
        }
        if (!authorName.trim() || (!plainText && !selectedGif && !uploadedImageUrl)) {
            alert("Please enter a name and at least a message or media.");
            return;
        }
        if (isUploadingImage) {
            setSubmitError('Image upload is still in progress.');
            return;
        }

        setIsSubmitting(true);
        setSubmitError(null);
        try {
            const postData = {
                authorName,
                messageText,
                giphyUrl: selectedGif?.images?.downsized_medium?.url ?? selectedGif?.images?.fixed_height?.url ?? null,
                uploadedImageUrl: uploadedImageUrl ?? null,
            };

            const res = await fetchWithCorrelation(`/api/boards/${boardId}/posts`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json; version=1' },
                body: JSON.stringify(postData)
            });

            if (res.ok) {
                const newPost = await res.json() as Post;
                if (newPost.editDeleteToken) {
                    setPostCapabilityToken(newPost.id, newPost.editDeleteToken);
                    // Avoid keeping the token in React state.
                    const newPostTyped = newPost as Post & { editDeleteToken?: string | null };
                    delete newPostTyped.editDeleteToken;
                }
                onPostAdded(newPost);
                onClose();
            } else {
                const rid = getRequestId(res);
                let errorMessage = "Failed to submit post.";
                try {
                    const payload = await res.json();
                    if (payload?.error && typeof payload.error === 'string') {
                        errorMessage = payload.error;
                    }
                } catch {
                    // Keep default message when backend did not return JSON.
                }
                if (rid) errorMessage += ` (ref: ${rid})`;
                setSubmitError(errorMessage);
            }
        } catch (err) {
            console.error("Error submitting post:", err);
            setSubmitError("Failed to submit post.");
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content add-post-modal" onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>Add your message</h2>
                    <button className="close-btn" onClick={onClose}>&times;</button>
                </div>

                <form onSubmit={handleSubmit} className="add-post-form">
                    <div className="form-group row-group">
                        <button
                            type="button"
                            className={`media-btn ${showGifSearch ? 'active' : ''} ${isUploadingImage ? 'disabled' : ''}`}
                            onClick={() => {
                                if (isUploadingImage) return;
                                if (uploadedImageUrl) {
                                    setUploadedImageUrl(null);
                                }
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
                            {isUploadingImage && (
                                <span className="uploading-indicator" data-testid="image-uploading-indicator">
                                    <span className="spinner" aria-hidden="true" /> Uploading…
                                </span>
                            )}
                            <input
                                type="file"
                                accept="image/png,image/jpeg"
                                data-testid="upload-image-input"
                                style={{ display: 'none' }}
                                disabled={isUploadingImage}
                                onChange={(e) => handleFileSelected(e.target.files?.[0] ?? null)}
                            />
                        </label>
                    </div>

                    {showGifSearch && (
                        <div className="gif-search-container">
                            {giphyUnconfigured ? (
                                <p className="gif-search-hint">
                                    GIF search is not configured. Set <code>GIPHY_API_KEY</code> (env) or <code>giphy.api-key</code> in the backend (e.g. <code>application-secret.properties</code>).
                                    Get a key at <a href="https://developers.giphy.com/" target="_blank" rel="noopener noreferrer">developers.giphy.com</a>.
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
                                                setSelectedGif(gif as GiphyGif);
                                                setUploadedImageUrl(null);
                                                setShowGifSearch(false);
                                            }}
                                        />
                                    </div>
                                </>
                            )}
                        </div>
                    )}

                    {selectedGif && (
                        <div className="selected-gif-preview">
                            <img
                                src={selectedGif.images?.fixed_height?.url ?? selectedGif.images?.downsized_medium?.url ?? ''}
                                alt="Selected GIF"
                            />
                            <button type="button" onClick={() => setSelectedGif(null)}>Remove</button>
                        </div>
                    )}

                    {uploadedImageUrl && (
                        <div className="selected-gif-preview">
                            <img src={uploadedImageUrl} alt="Selected upload" />
                            <button type="button" onClick={() => { setUploadedImageUrl(null); }}>
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
                        <button type="submit" className="btn-primary" disabled={isSubmitting || isUploadingImage}>
                            {isSubmitting ? 'Posting...' : isUploadingImage ? 'Uploading image...' : 'Post'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
