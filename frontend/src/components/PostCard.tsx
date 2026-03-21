import { useEffect, useState } from 'react';
import './PostCard.css';
import type { Post } from '../types/post';
import DOMPurify from 'dompurify';
import { Pencil, Trash2 } from 'lucide-react';

function isHtmlContent(text: string): boolean {
    return /<[a-z][\s\S]*>/i.test(text);
}

export default function PostCard({
    post,
    canEdit,
    onEdit,
    onDelete,
}: {
    post: Post;
    canEdit?: boolean;
    onEdit?: (post: Post) => void;
    onDelete?: (post: Post) => void;
}) {
    const [expandedImageUrl, setExpandedImageUrl] = useState<string | null>(null);
    const [expandedImageAlt, setExpandedImageAlt] = useState('Image');

    useEffect(() => {
        if (!expandedImageUrl) return;
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setExpandedImageUrl(null);
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [expandedImageUrl]);

    const openImagePreview = (imageUrl: string, imageAlt: string) => {
        setExpandedImageUrl(imageUrl);
        setExpandedImageAlt(imageAlt);
    };

    const renderMessageContent = () => {
        const message = post.messageText ?? '';
        if (message && isHtmlContent(message)) {
            const safeHtml = DOMPurify.sanitize(message, { USE_PROFILES: { html: true } });
            return (
                <div 
                    className="post-text rich-text-content"
                    dangerouslySetInnerHTML={{ __html: safeHtml }}
                />
            );
        }
        return message ? <p className="post-text">{message}</p> : null;
    };

    return (
        <>
            <div className="post-card">
                {post.uploadedImageUrl && (
                    <div className="post-media">
                        <button
                            type="button"
                            className="post-image-button"
                            aria-label="Open image in large view"
                            onClick={() => openImagePreview(post.uploadedImageUrl ?? '', 'Uploaded image')}
                        >
                            <img src={post.uploadedImageUrl} alt="Uploaded image" />
                        </button>
                    </div>
                )}
                {post.giphyUrl && (
                    <div className="post-media">
                        <button
                            type="button"
                            className="post-image-button"
                            aria-label="Open GIF in large view"
                            onClick={() => openImagePreview(post.giphyUrl ?? '', 'GIF')}
                        >
                            <img src={post.giphyUrl} alt="GIF" />
                        </button>
                    </div>
                )}
                <div className="post-content">
                    {renderMessageContent()}
                    <div className="post-footer">
                        {canEdit && (
                            <div className="post-actions" aria-label="Post actions">
                                <button
                                    type="button"
                                    className="icon-button post-action-btn"
                                    data-tooltip="Edit post"
                                    aria-label="Edit post"
                                    data-testid="post-edit-button"
                                    onClick={() => onEdit?.(post)}
                                >
                                    <Pencil size={16} />
                                </button>
                                <button
                                    type="button"
                                    className="icon-button post-action-btn"
                                    data-tooltip="Delete post"
                                    aria-label="Delete post"
                                    data-testid="post-delete-button"
                                    onClick={() => onDelete?.(post)}
                                >
                                    <Trash2 size={16} />
                                </button>
                            </div>
                        )}
                        <span className="post-author">From {post.authorName}</span>
                    </div>
                </div>
            </div>
            {expandedImageUrl && (
                <div className="image-lightbox-overlay" onClick={() => setExpandedImageUrl(null)}>
                    <div className="image-lightbox-content" onClick={(event) => event.stopPropagation()}>
                        <button
                            type="button"
                            className="image-lightbox-close"
                            aria-label="Close image preview"
                            onClick={() => setExpandedImageUrl(null)}
                        >
                            &times;
                        </button>
                        <img src={expandedImageUrl} alt={expandedImageAlt} className="image-lightbox-img" />
                    </div>
                </div>
            )}
        </>
    );
}
