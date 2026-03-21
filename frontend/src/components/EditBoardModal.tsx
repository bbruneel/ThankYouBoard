import React, { useState } from 'react';
import './AddPostModal.css';

interface BoardForEdit {
  id: string;
  title: string;
  recipientName: string;
}

interface EditBoardModalProps {
  board: BoardForEdit;
  isOpen: boolean;
  onClose: () => void;
  onSave: (values: { title: string; recipientName: string }) => Promise<void> | void;
}

export default function EditBoardModal({ board, isOpen, onClose, onSave }: EditBoardModalProps) {
  const [title, setTitle] = useState(board.title);
  const [recipientName, setRecipientName] = useState(board.recipientName);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !recipientName.trim()) {
      setError('Please provide both a title and recipient name.');
      return;
    }
    setIsSubmitting(true);
    setError(null);
    try {
      await onSave({ title: title.trim(), recipientName: recipientName.trim() });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to update board.';
      setError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content add-post-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Edit board</h2>
          <button className="close-btn" onClick={onClose} aria-label="Close edit board modal">
            &times;
          </button>
        </div>
        <form onSubmit={handleSubmit} className="add-post-form">
          <div className="form-group main-message-group">
            <label className="field-label" htmlFor="edit-board-title">
              Board title
            </label>
            <input
              id="edit-board-title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={140}
            />
          </div>
          <div className="form-group main-message-group">
            <label className="field-label" htmlFor="edit-board-recipient">
              Recipient name
            </label>
            <input
              id="edit-board-recipient"
              type="text"
              value={recipientName}
              onChange={(e) => setRecipientName(e.target.value)}
              maxLength={140}
            />
          </div>
          {error && <p className="error-text">{error}</p>}
          <div className="form-footer edit-board-footer">
            <button type="button" className="btn-secondary" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={isSubmitting} data-testid="save-board-button">
              {isSubmitting ? 'Saving...' : 'Save changes'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

