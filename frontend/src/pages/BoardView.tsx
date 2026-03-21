import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useAuth0 } from '@auth0/auth0-react';
import PostCard from '../components/PostCard';
import AddPostModal from '../components/AddPostModal';
import EditBoardModal from '../components/EditBoardModal';
import EditPostModal from '../components/EditPostModal';
import { Plus, LayoutDashboard, Pencil, Trash2, Download } from 'lucide-react';
import { ThemeToggle } from '../components/ThemeToggle';
import { fetchWithCorrelation, getRequestId } from '../api/fetchWithCorrelation';
import type { Post } from '../types/post';
import { clearPostCapabilityToken, getPostCapabilityToken } from '../utils/postCapabilityTokens';
import './BoardView.css';

interface Board {
    id: string;
    title: string;
    recipientName: string;
    canEdit?: boolean;
}

function errorWithRequestId(msg: string, res: Response): string {
    const rid = getRequestId(res);
    return rid ? `${msg} (ref: ${rid})` : msg;
}

export default function BoardView() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { isAuthenticated, isLoading, getAccessTokenSilently } = useAuth0();
    const [board, setBoard] = useState<Board | null>(null);
    const [posts, setPosts] = useState<Post[]>([]);
    const [loading, setLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isEditOpen, setIsEditOpen] = useState(false);
    const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
    const [editingPost, setEditingPost] = useState<Post | null>(null);
    const [editPostAccessToken, setEditPostAccessToken] = useState<string | null>(null);
    const [editPostCapabilityToken, setEditPostCapabilityToken] = useState<string | null>(null);
    const [deletingPost, setDeletingPost] = useState<Post | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);
    const [pdfJobStatus, setPdfJobStatus] = useState<string | null>(null);

    const PDF_POLL_INTERVAL = Number(import.meta.env.VITE_PDF_JOB_POLL_INTERVAL_MS) || 2000;

    useEffect(() => {
        if (isLoading) {
            return;
        }

        const fetchData = async () => {
            try {
                setLoading(true);

                const headers: HeadersInit = {
                    'Accept': 'application/json; version=1',
                };

                if (isAuthenticated) {
                    try {
                        const token = await getAccessTokenSilently();
                        (headers as Record<string, string>).Authorization = `Bearer ${token}`;
                    } catch (err) {
                        console.error('Failed to get access token for board view', err);
                    }
                }

                const boardRes = await fetchWithCorrelation(`/api/boards/${id}`, { headers });
                if (boardRes.ok) {
                    const boardData = await boardRes.json();
                    setBoard(boardData);
                } else {
                    setBoard({ id: id || '', title: "We'll Miss You", recipientName: "Sarah", canEdit: false });
                }

                const postsRes = await fetchWithCorrelation(`/api/boards/${id}/posts`, { headers: { 'Accept': 'application/json; version=1' } });
                if (postsRes.ok) {
                    const postsData = await postsRes.json();
                    setPosts(postsData);
                } else {
                    setPosts([
                        { id: '1', authorName: 'Alice', messageText: 'Thanks for everything!', giphyUrl: 'https://media.giphy.com/media/3o7TKoWXm3okO1kgHC/giphy.gif', createdAt: new Date().toISOString() }
                    ]);
                }
            } catch (error) {
                console.error("Error fetching data", error);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [id, isAuthenticated, isLoading, getAccessTokenSilently]);

    if (loading) return <div className="loading">Loading board...</div>;
    if (!board) return <div className="error">Board not found</div>;

    const handleSaveBoard = async (values: { title: string; recipientName: string }) => {
        if (!id) {
            throw new Error('Missing board id.');
        }
        if (!isAuthenticated) {
            throw new Error('You must be logged in to edit this board.');
        }
        setActionError(null);
        const token = await getAccessTokenSilently();
        const res = await fetchWithCorrelation(`/api/boards/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                Accept: 'application/json; version=1',
                Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({
                title: values.title,
                recipientName: values.recipientName,
            }),
        });
        if (!res.ok) {
            if (res.status === 403) {
                throw new Error(errorWithRequestId('You can only edit your own boards.', res));
            }
            if (res.status === 404) {
                throw new Error(errorWithRequestId('Board not found.', res));
            }
            throw new Error(errorWithRequestId('Failed to update board.', res));
        }
        const updated = await res.json();
        setBoard((prev) =>
            prev
                ? {
                      ...prev,
                      title: updated.title,
                      recipientName: updated.recipientName,
                  }
                : prev,
        );
        setIsEditOpen(false);
    };

    const handleDeleteBoard = async () => {
        if (!id) {
            setActionError('Missing board id.');
            return;
        }
        if (!isAuthenticated) {
            setActionError('You must be logged in to delete this board.');
            return;
        }
        setActionError(null);
        const token = await getAccessTokenSilently();
        const res = await fetchWithCorrelation(`/api/boards/${id}`, {
            method: 'DELETE',
            headers: {
                Accept: 'application/json; version=1',
                Authorization: `Bearer ${token}`,
            },
        });
        if (res.status === 204) {
            navigate('/');
            return;
        }
        if (res.status === 403) {
            setActionError(errorWithRequestId('You can only delete your own boards.', res));
            return;
        }
        if (res.status === 404) {
            setActionError(errorWithRequestId('Board not found.', res));
            return;
        }
        setActionError(errorWithRequestId('Failed to delete board.', res));
    };

    const handleDownloadPdf = async () => {
        if (!id) {
            setActionError('Missing board id.');
            return;
        }
        if (!isAuthenticated) {
            setActionError('You must be logged in to download this board PDF.');
            return;
        }
        if (pdfJobStatus === 'PENDING' || pdfJobStatus === 'RUNNING') {
            return;
        }
        setActionError(null);
        setPdfJobStatus('PENDING');
        try {
            const token = await getAccessTokenSilently();
            const createRes = await fetchWithCorrelation(`/api/boards/${id}/pdf-jobs`, {
                method: 'POST',
                headers: {
                    Accept: 'application/json; version=1',
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({
                    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                }),
            });
            if (createRes.status === 403) {
                setPdfJobStatus(null);
                setActionError(errorWithRequestId('You can only download PDFs for your own boards.', createRes));
                return;
            }
            if (createRes.status === 404) {
                setPdfJobStatus(null);
                setActionError(errorWithRequestId('Board not found.', createRes));
                return;
            }
            if (!createRes.ok) {
                setPdfJobStatus(null);
                setActionError(errorWithRequestId('Failed to start PDF generation.', createRes));
                return;
            }

            const job = await createRes.json();
            const statusUrl = job.statusUrl as string;
            const maxPolls = Math.max(10, Math.ceil(120_000 / PDF_POLL_INTERVAL));
            let pollCount = 0;

            const poll = async (): Promise<void> => {
                if (++pollCount > maxPolls) {
                    setPdfJobStatus(null);
                    setActionError('PDF generation timed out. Please try again.');
                    return;
                }
                const statusRes = await fetchWithCorrelation(statusUrl, {
                    headers: {
                        Accept: 'application/json; version=1',
                        Authorization: `Bearer ${token}`,
                    },
                });
                if (!statusRes.ok) {
                    setPdfJobStatus(null);
                    setActionError(errorWithRequestId('Failed to check PDF status.', statusRes));
                    return;
                }
                const statusData = await statusRes.json();
                setPdfJobStatus(statusData.status);

                if (statusData.status === 'SUCCEEDED' && statusData.downloadUrl) {
                    setPdfJobStatus(null);
                    const downloadUrl = statusData.downloadUrl as string;
                    // Relative URL (e.g. Spring Boot /download): must use fetch so we can send Authorization.
                    // Absolute URL (e.g. S3 presigned): use navigation to avoid CORS.
                    const isRelative =
                        downloadUrl.startsWith('/') ||
                        (typeof window !== 'undefined' &&
                            new URL(downloadUrl, window.location.origin).origin === window.location.origin);
                    if (isRelative) {
                        const dlRes = await fetchWithCorrelation(downloadUrl, {
                            headers: {
                                Accept: 'application/pdf; version=1',
                                Authorization: `Bearer ${token}`,
                            },
                        });
                        if (!dlRes.ok) {
                            setActionError('Failed to download PDF file.');
                            return;
                        }
                        const blob = await dlRes.blob();
                        const url = window.URL.createObjectURL(blob);
                        const link = document.createElement('a');
                        const disposition = dlRes.headers.get('Content-Disposition');
                        const filenameMatch = disposition?.match(/filename="?([^";]+)"?/);
                        const filename =
                            filenameMatch?.[1] ||
                            `${(board?.title || 'board').toLowerCase().replace(/[^a-z0-9]+/g, '-')}-${id}.pdf`;
                        link.href = url;
                        link.download = filename;
                        document.body.appendChild(link);
                        link.click();
                        document.body.removeChild(link);
                        window.setTimeout(() => window.URL.revokeObjectURL(url), 1500);
                    } else {
                        window.location.href = downloadUrl;
                    }
                    return;
                }

                if (statusData.status === 'FAILED') {
                    setPdfJobStatus(null);
                    setActionError(statusData.errorMessage || 'PDF generation failed.');
                    return;
                }

                await new Promise(resolve => setTimeout(resolve, PDF_POLL_INTERVAL));
                return poll();
            };

            await poll();
        } catch {
            setPdfJobStatus(null);
            setActionError('Failed to download board PDF.');
        }
    };

    const handleEditPost = async (post: Post) => {
        setActionError(null);
        const anonToken = getPostCapabilityToken(post.id);

        // Anonymous edit: allow only if we have the per-post capability token.
        if (!isAuthenticated) {
            if (!anonToken) {
                setActionError('You don\'t have permission to edit this post.');
                return;
            }
            setEditPostAccessToken(null);
            setEditPostCapabilityToken(anonToken);
            setEditingPost(post);
            return;
        }

        // Auth0 edit: board owner path (or any authenticated user that has access via UI).
        try {
            const token = await getAccessTokenSilently();
            setEditPostAccessToken(token);
            setEditPostCapabilityToken(null);
            setEditingPost(post);
        } catch {
            setActionError('Failed to get access token.');
        }
    };

    const handleDeletePostRequest = (post: Post) => {
        setActionError(null);
        setDeletingPost(post);
    };

    const handleDeletePost = async () => {
        if (!id) {
            setActionError('Missing board id.');
            return;
        }
        if (!deletingPost) {
            setActionError('Missing post.');
            return;
        }
        setActionError(null);

        const anonToken = getPostCapabilityToken(deletingPost.id);

        const headers: Record<string, string> = {
            Accept: 'application/json; version=1',
        };

        if (isAuthenticated) {
            const token = await getAccessTokenSilently();
            headers.Authorization = `Bearer ${token}`;
        } else {
            if (!anonToken) {
                setActionError('You don\'t have permission to delete this post.');
                return;
            }
            headers['X-Post-Capability-Token'] = anonToken;
        }

        const res = await fetchWithCorrelation(`/api/boards/${id}/posts/${deletingPost.id}`, {
            method: 'DELETE',
            headers: {
                ...headers,
            },
        });
        if (res.status === 204) {
            setPosts((prev) => prev.filter((p) => p.id !== deletingPost.id));
            clearPostCapabilityToken(deletingPost.id);
            setDeletingPost(null);
            return;
        }
        if (res.status === 403) {
            setActionError(errorWithRequestId(
                isAuthenticated
                    ? 'You can only delete posts on boards you own.'
                    : 'Your permission to delete this post has expired.',
                res,
            ));
            return;
        }
        if (res.status === 404) {
            setActionError(errorWithRequestId('Post not found.', res));
            return;
        }
        setActionError(errorWithRequestId('Failed to delete post.', res));
    };

    return (
        <div className="board-page">
            <header className="board-header">
                <div className="header-content">
                    <div className="header-left">
                        <Link to="/" className="dashboard-link" aria-label="Back to Dashboard">
                            <LayoutDashboard size={20} /> Dashboard
                        </Link>
                        <h1>{board.title}{board.recipientName ? `, ${board.recipientName}!` : '!'}</h1>
                    </div>
                    <div className="flex-btn">
                        <ThemeToggle />
                        {board.canEdit && (
                            <>
                                <button
                                    type="button"
                                    className="icon-button"
                                    data-tooltip="Edit board"
                                    aria-label="Edit board"
                                    onClick={() => {
                                        setActionError(null);
                                        setIsEditOpen(true);
                                    }}
                                    data-testid="board-edit-button"
                                >
                                    <Pencil size={18} />
                                </button>
                                <button
                                    type="button"
                                    className="icon-button"
                                    data-tooltip={pdfJobStatus ? 'Preparing PDF…' : 'Download board PDF'}
                                    aria-label={pdfJobStatus ? 'Preparing PDF…' : 'Download board PDF'}
                                    onClick={handleDownloadPdf}
                                    disabled={pdfJobStatus === 'PENDING' || pdfJobStatus === 'RUNNING'}
                                    data-testid="board-download-pdf-button"
                                    data-pdf-status={pdfJobStatus || undefined}
                                >
                                    {pdfJobStatus ? <span className="spinner-icon" /> : <Download size={18} />}
                                </button>
                                <button
                                    type="button"
                                    className="icon-button"
                                    data-tooltip="Delete board"
                                    aria-label="Delete board"
                                    onClick={() => {
                                        setActionError(null);
                                        setIsDeleteConfirmOpen(true);
                                    }}
                                    data-testid="board-delete-button"
                                >
                                    <Trash2 size={18} />
                                </button>
                            </>
                        )}
                        <button
                            className="btn-primary flex-btn"
                            onClick={() => setIsModalOpen(true)}
                            data-testid="add-post-button"
                        >
                            <Plus size={20} /> Add Post
                        </button>
                    </div>
                </div>
            </header>

            <main className="masonry-grid">
                {posts.map((post) => {
                    const anonToken = getPostCapabilityToken(post.id);
                    const canEditPost = !!board.canEdit || !!anonToken;
                    return (
                        <PostCard
                            key={post.id}
                            post={post}
                            canEdit={canEditPost}
                            onEdit={handleEditPost}
                            onDelete={handleDeletePostRequest}
                        />
                    );
                })}
            </main>

            {isModalOpen && (
                <AddPostModal
                    boardId={board.id}
                    onClose={() => setIsModalOpen(false)}
                    onPostAdded={(newPost) => setPosts([...posts, newPost])}
                />
            )}
            {isEditOpen && (
                <EditBoardModal
                    board={board}
                    isOpen={isEditOpen}
                    onClose={() => setIsEditOpen(false)}
                    onSave={handleSaveBoard}
                />
            )}
            {editingPost && (editPostAccessToken || editPostCapabilityToken) && (
                <EditPostModal
                    boardId={board.id}
                    post={editingPost}
                    accessToken={editPostAccessToken}
                    capabilityToken={editPostCapabilityToken}
                    onClose={() => {
                        setEditingPost(null);
                        setEditPostAccessToken(null);
                        setEditPostCapabilityToken(null);
                    }}
                    onPostUpdated={(updated) => {
                        setPosts((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
                    }}
                />
            )}
            {isDeleteConfirmOpen && (
                <div className="modal-overlay" onClick={() => setIsDeleteConfirmOpen(false)}>
                    <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Delete board</h2>
                            <button
                                className="close-btn"
                                onClick={() => setIsDeleteConfirmOpen(false)}
                                aria-label="Close delete confirmation"
                            >
                                &times;
                            </button>
                        </div>
                        <div className="modal-body">
                            <p>
                                Are you sure you want to delete this board? This action cannot be undone, and all posts
                                will be removed.
                            </p>
                            {actionError && <p className="error-text">{actionError}</p>}
                        </div>
                        <div className="modal-footer">
                            <button
                                type="button"
                                className="btn-secondary"
                                onClick={() => setIsDeleteConfirmOpen(false)}
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                className="btn-primary"
                                onClick={handleDeleteBoard}
                                data-testid="confirm-delete-board-button"
                            >
                                Delete board
                            </button>
                        </div>
                    </div>
                </div>
            )}
            {deletingPost && (
                <div className="modal-overlay" onClick={() => setDeletingPost(null)}>
                    <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Delete post</h2>
                            <button
                                className="close-btn"
                                onClick={() => setDeletingPost(null)}
                                aria-label="Close delete post confirmation"
                            >
                                &times;
                            </button>
                        </div>
                        <div className="modal-body">
                            <p>Are you sure you want to delete this post? This action cannot be undone.</p>
                            {actionError && <p className="error-text">{actionError}</p>}
                        </div>
                        <div className="modal-footer">
                            <button type="button" className="btn-secondary" onClick={() => setDeletingPost(null)}>
                                Cancel
                            </button>
                            <button
                                type="button"
                                className="btn-primary"
                                onClick={handleDeletePost}
                                data-testid="confirm-delete-post-button"
                            >
                                Delete post
                            </button>
                        </div>
                    </div>
                </div>
            )}
            {actionError && !isDeleteConfirmOpen && (
                <div className="action-error-banner">
                    <span>{actionError}</span>
                </div>
            )}
        </div>
    );
}
