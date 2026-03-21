import { useEffect, useState } from 'react';
import { useAuth0 } from '@auth0/auth0-react';
import { Link, useNavigate } from 'react-router-dom';
import { Pencil, Plus, Sparkles, Trash2 } from 'lucide-react';
import EditBoardModal from '../components/EditBoardModal';
import { ThemeToggle } from '../components/ThemeToggle';
import { fetchWithCorrelation, getRequestId } from '../api/fetchWithCorrelation';
import './Dashboard.css';

interface Board {
    id: string;
    title: string;
    recipientName: string;
}

export default function Dashboard() {
    const authConfigured = Boolean(
        import.meta.env.VITE_AUTH0_DOMAIN && import.meta.env.VITE_AUTH0_CLIENT_ID,
    );

    if (!authConfigured) {
        return <DashboardWithoutAuth0 />;
    }

    return <DashboardWithAuth0 />;
}

function DashboardWithoutAuth0() {
    return (
        <div className="dashboard-page">
            <header className="dashboard-header">
                <div className="dashboard-header-content">
                    <h1 className="dashboard-logo">Thank You Boards</h1>
                    <div className="dashboard-header-actions">
                        <ThemeToggle />
                        <button
                            type="button"
                            className="btn-primary"
                            disabled
                            title="Set VITE_AUTH0_DOMAIN and VITE_AUTH0_CLIENT_ID to enable login."
                        >
                            Log in
                        </button>
                    </div>
                </div>
            </header>

            <main className="dashboard-main">
                <div className="dashboard-unauthenticated">
                    <h2 className="hero-title">Show appreciation before they move on.</h2>
                    <p className="hero-subtitle">
                        Create beautiful, collaborative Thank You boards for colleagues, friends, and loved ones in seconds.
                    </p>
                    <p className="dashboard-empty" style={{ opacity: 0.5 }}>
                        (Auth0 isn&apos;t configured for this frontend yet. Set `VITE_AUTH0_DOMAIN` and
                        `VITE_AUTH0_CLIENT_ID` and restart `npm run dev`.)
                    </p>
                </div>
            </main>
        </div>
    );
}

function DashboardWithAuth0() {
    const {
        isAuthenticated,
        loginWithRedirect,
        logout,
        getAccessTokenSilently,
        user,
    } = useAuth0();
    const navigate = useNavigate();
    const [boards, setBoards] = useState<Board[]>([]);
    const [loading, setLoading] = useState(true);
    const [hasTriggeredAuth0Logout, setHasTriggeredAuth0Logout] = useState(false);
    const [isCreateOpen, setIsCreateOpen] = useState(false);
    const [createTitle, setCreateTitle] = useState('');
    const [createRecipient, setCreateRecipient] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [editingBoard, setEditingBoard] = useState<Board | null>(null);
    const [deletingBoard, setDeletingBoard] = useState<Board | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!isAuthenticated) {
            setHasTriggeredAuth0Logout(false);
        }
    }, [isAuthenticated]);

    const ACCESS_TOKEN_TIMEOUT_MS = 8000;
    const getAccessTokenSilentlyWithTimeout = async (): Promise<string> => {
        return await Promise.race([
            getAccessTokenSilently(),
            new Promise<string>((_, reject) =>
                setTimeout(() => reject(new Error('getAccessTokenSilently timed out')), ACCESS_TOKEN_TIMEOUT_MS),
            ),
        ]);
    };

    useEffect(() => {
        const fetchBoards = async () => {
            if (!isAuthenticated) {
                setBoards([]);
                setLoading(false);
                return;
            }
            try {
                setLoading(true);
                const token = await getAccessTokenSilentlyWithTimeout();
                const res = await fetchWithCorrelation('/api/boards', {
                    headers: {
                        Accept: 'application/json; version=1',
                        Authorization: `Bearer ${token}`,
                    },
                });
                if (res.ok) {
                    const data = await res.json();
                    setBoards(data);
                } else {
                    setBoards([]);
                    if (res.status === 401 && !hasTriggeredAuth0Logout) {
                        setHasTriggeredAuth0Logout(true);
                        logout({
                            logoutParams: { returnTo: window.location.origin },
                        });
                    }
                }
            } catch {
                setBoards([]);
            } finally {
                setLoading(false);
            }
        };
        fetchBoards();
    }, [isAuthenticated, getAccessTokenSilently, logout, hasTriggeredAuth0Logout]);

    const handleCreateBoard = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!createTitle.trim() || !createRecipient.trim() || !isAuthenticated) return;
        setIsSubmitting(true);
        setError(null);
        try {
            const token = await getAccessTokenSilentlyWithTimeout();
            const res = await fetchWithCorrelation('/api/boards', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Accept: 'application/json; version=1',
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({
                    title: createTitle.trim(),
                    recipientName: createRecipient.trim(),
                }),
            });
            if (!res.ok) {
                const rid = getRequestId(res);
                let errorMessage = 'Failed to create board';
                try {
                    const payload = await res.json();
                    if (payload?.error && typeof payload.error === 'string') {
                        errorMessage = payload.error;
                    }
                } catch {
                    // Backend may return non-JSON (or empty body).
                    const msg = await res.text();
                    if (msg) errorMessage = msg;
                }
                setError(errorMessage + (rid ? ` (ref: ${rid})` : ''));
                return;
            }
            const newBoard = await res.json();
            setBoards([...boards, newBoard]);
            setIsCreateOpen(false);
            setCreateTitle('');
            setCreateRecipient('');
            navigate(`/board/${newBoard.id}`);
        } catch {
            setError('Network error');
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleSaveBoard = async (values: { title: string; recipientName: string }) => {
        if (!editingBoard) {
            throw new Error('Missing board id.');
        }
        if (!isAuthenticated) {
            throw new Error('You must be logged in to edit this board.');
        }
        const token = await getAccessTokenSilentlyWithTimeout();
        const res = await fetchWithCorrelation(`/api/boards/${editingBoard.id}`, {
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
            const rid = getRequestId(res);
            const suffix = rid ? ` (ref: ${rid})` : '';
            if (res.status === 403) {
                throw new Error('You can only edit your own boards.' + suffix);
            }
            if (res.status === 404) {
                throw new Error('Board not found.' + suffix);
            }
            throw new Error('Failed to update board.' + suffix);
        }
        const updated = await res.json();
        setBoards((prev) =>
            prev.map((board) =>
                board.id === editingBoard.id
                    ? {
                          ...board,
                          title: updated.title,
                          recipientName: updated.recipientName,
                      }
                    : board,
            ),
        );
        setEditingBoard(null);
    };

    const handleDeleteBoard = async () => {
        if (!deletingBoard) {
            setActionError('Missing board id.');
            return;
        }
        if (!isAuthenticated) {
            setActionError('You must be logged in to delete this board.');
            return;
        }
        setActionError(null);
        const token = await getAccessTokenSilentlyWithTimeout();
        const res = await fetchWithCorrelation(`/api/boards/${deletingBoard.id}`, {
            method: 'DELETE',
            headers: {
                Accept: 'application/json; version=1',
                Authorization: `Bearer ${token}`,
            },
        });
        if (res.status === 204) {
            setBoards((prev) => prev.filter((board) => board.id !== deletingBoard.id));
            setDeletingBoard(null);
            return;
        }
        const rid = getRequestId(res);
        const suffix = rid ? ` (ref: ${rid})` : '';
        if (res.status === 403) {
            setActionError('You can only delete your own boards.' + suffix);
            return;
        }
        if (res.status === 404) {
            setActionError('Board not found.' + suffix);
            return;
        }
        setActionError('Failed to delete board.' + suffix);
    };

    if (loading) return <div className="dashboard-loading">Loading boards...</div>;

    return (
        <div className="dashboard-page">
            <header className="dashboard-header">
                <div className="dashboard-header-content">
                    <h1 className="dashboard-logo">Thank You Boards</h1>
                    <div className="dashboard-header-actions">
                        <ThemeToggle />
                        {isAuthenticated ? (
                            <>
                                <div className="user-profile-menu">
                                    <img
                                        src={user?.picture || 'https://www.gravatar.com/avatar/?d=mp'}
                                        alt="Profile"
                                        className="user-avatar"
                                        referrerPolicy="no-referrer"
                                    />
                                    <div className="user-info">
                                        <span className="user-name">{user?.name || user?.email?.split('@')[0]}</span>
                                        <span className="user-email">{user?.email}</span>
                                    </div>
                                    <button
                                        type="button"
                                        className="btn-logout"
                                        onClick={() =>
                                            logout({
                                                logoutParams: { returnTo: window.location.origin },
                                            })
                                        }
                                    >
                                        Logout
                                    </button>
                                </div>
                            </>
                        ) : (
                            <button
                                type="button"
                                className="btn-primary"
                                onClick={() => loginWithRedirect()}
                            >
                                Log in
                            </button>
                        )}
                    </div>
                </div>
            </header>

            <main className="dashboard-main">
                {isAuthenticated ? (
                    <>
                        <h2 className="dashboard-title">Your boards</h2>
                        <button
                            type="button"
                            className="btn-primary flex-btn dashboard-create-button"
                            onClick={() => setIsCreateOpen(true)}
                        >
                            <Plus size={20} /> Create a Board
                        </button>
                        {boards.length === 0 ? (
                            <div className="dashboard-empty-state">
                                <div className="dashboard-empty-icon">
                                    <Sparkles size={32} />
                                </div>
                                <h3 className="dashboard-empty-title">No boards yet</h3>
                                <p className="dashboard-empty-subtitle">
                                    Start by creating a board to collect messages of appreciation.
                                </p>
                            </div>
                        ) : (
                            <ul className="board-list">
                                {boards.map((board) => (
                                    <li key={board.id} className="board-list-item" data-testid="dashboard-board-tile">
                                        <article className="board-list-card">
                                            <Link to={`/board/${board.id}`} className="board-list-card-link">
                                                <span className="board-list-card-title">{board.title}</span>
                                                <span className="board-list-card-recipient">
                                                    For {board.recipientName}
                                                </span>
                                            </Link>
                                            <div className="board-list-card-actions">
                                                <button
                                                    type="button"
                                                    className="icon-button"
                                                    data-tooltip="Edit board"
                                                    aria-label={`Edit ${board.title}`}
                                                    data-testid="dashboard-board-edit-button"
                                                    onClick={() => {
                                                        setActionError(null);
                                                        setEditingBoard(board);
                                                    }}
                                                >
                                                    <Pencil size={18} />
                                                </button>
                                                <button
                                                    type="button"
                                                    className="icon-button"
                                                    data-tooltip="Delete board"
                                                    aria-label={`Delete ${board.title}`}
                                                    data-testid="dashboard-board-delete-button"
                                                    onClick={() => {
                                                        setActionError(null);
                                                        setDeletingBoard(board);
                                                    }}
                                                >
                                                    <Trash2 size={18} />
                                                </button>
                                            </div>
                                        </article>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </>
                ) : (
                    <div className="dashboard-unauthenticated">
                        <h2 className="hero-title">Show appreciation before they move on.</h2>
                        <p className="hero-subtitle">
                            Create beautiful, collaborative Thank You boards for colleagues, friends, and loved ones in seconds.
                        </p>
                        <button
                            type="button"
                            className="btn-primary"
                            onClick={() => loginWithRedirect()}
                        >
                            Log in to get started
                        </button>
                    </div>
                )}
            </main>

            {isCreateOpen && (
                <div className="modal-overlay" onClick={() => !isSubmitting && setIsCreateOpen(false)}>
                    <div
                        className="modal-content create-board-modal"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="modal-header">
                            <h2>Create a Board</h2>
                            <button
                                type="button"
                                className="close-btn"
                                onClick={() => !isSubmitting && setIsCreateOpen(false)}
                            >
                                &times;
                            </button>
                        </div>
                        <form onSubmit={handleCreateBoard} className="create-board-form">
                            <div className="form-group">
                                <label htmlFor="board-title">Board title</label>
                                <input
                                    id="board-title"
                                    type="text"
                                    placeholder="e.g. We'll Miss You!"
                                    value={createTitle}
                                    onChange={(e) => setCreateTitle(e.target.value)}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label htmlFor="board-recipient">Recipient name</label>
                                <input
                                    id="board-recipient"
                                    type="text"
                                    placeholder="e.g. Sarah"
                                    value={createRecipient}
                                    onChange={(e) => setCreateRecipient(e.target.value)}
                                    required
                                />
                            </div>
                            {error && <p className="form-error">{error}</p>}
                            <button
                                type="submit"
                                className="btn-primary btn-full"
                                disabled={isSubmitting}
                            >
                                {isSubmitting ? 'Creating...' : 'Create Board'}
                            </button>
                        </form>
                    </div>
                </div>
            )}
            {editingBoard && (
                <EditBoardModal
                    board={editingBoard}
                    isOpen={Boolean(editingBoard)}
                    onClose={() => {
                        setEditingBoard(null);
                        setActionError(null);
                    }}
                    onSave={handleSaveBoard}
                />
            )}
            {deletingBoard && (
                <div className="modal-overlay" onClick={() => setDeletingBoard(null)}>
                    <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Delete board</h2>
                            <button
                                className="close-btn"
                                onClick={() => setDeletingBoard(null)}
                                aria-label="Close delete confirmation"
                            >
                                &times;
                            </button>
                        </div>
                        <div className="modal-body">
                            <p>
                                Are you sure you want to delete <strong>{deletingBoard.title}</strong>? This action
                                cannot be undone, and all posts will be removed.
                            </p>
                            {actionError && <p className="error-text">{actionError}</p>}
                        </div>
                        <div className="modal-footer">
                            <button
                                type="button"
                                className="btn-secondary"
                                onClick={() => setDeletingBoard(null)}
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                className="btn-primary"
                                data-testid="dashboard-confirm-delete-board-button"
                                onClick={handleDeleteBoard}
                            >
                                Delete board
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
