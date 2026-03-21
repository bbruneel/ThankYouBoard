import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Underline from '@tiptap/extension-underline';
import TextAlign from '@tiptap/extension-text-align';
import Placeholder from '@tiptap/extension-placeholder';
import { useState, useRef, useEffect, useCallback } from 'react';
import data from '@emoji-mart/data';
import Picker from '@emoji-mart/react';
import './RichTextEditor.css';

interface RichTextEditorProps {
    value: string;
    onChange: (html: string) => void;
    placeholder?: string;
}

export default function RichTextEditor({ value, onChange, placeholder = 'Add a message...' }: RichTextEditorProps) {
    const [showEmojiPicker, setShowEmojiPicker] = useState(false);
    const emojiButtonRef = useRef<HTMLButtonElement>(null);
    const emojiPickerRef = useRef<HTMLDivElement>(null);

    const editor = useEditor({
        extensions: [
            StarterKit.configure({
                bulletList: {
                    keepMarks: true,
                    keepAttributes: false,
                },
                blockquote: {
                    HTMLAttributes: {
                        class: 'rich-text-blockquote',
                    },
                },
            }),
            Underline,
            TextAlign.configure({
                types: ['heading', 'paragraph'],
            }),
            Placeholder.configure({
                placeholder,
            }),
        ],
        content: value,
        onUpdate: ({ editor }) => {
            onChange(editor.getHTML());
        },
    });

    useEffect(() => {
        if (editor && value !== editor.getHTML()) {
            editor.commands.setContent(value);
        }
    }, [value, editor]);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (
                showEmojiPicker &&
                emojiPickerRef.current &&
                !emojiPickerRef.current.contains(event.target as Node) &&
                emojiButtonRef.current &&
                !emojiButtonRef.current.contains(event.target as Node)
            ) {
                setShowEmojiPicker(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [showEmojiPicker]);

    const handleEmojiSelect = useCallback((emoji: { native: string }) => {
        if (editor) {
            editor.chain().focus().insertContent(emoji.native).run();
        }
        setShowEmojiPicker(false);
    }, [editor]);

    if (!editor) {
        return null;
    }

    return (
        <div className="rich-text-editor">
            <div className="editor-toolbar">
                <div className="toolbar-group">
                    <button
                        ref={emojiButtonRef}
                        type="button"
                        className={`toolbar-btn emoji-btn ${showEmojiPicker ? 'active' : ''}`}
                        onClick={() => setShowEmojiPicker(!showEmojiPicker)}
                        title="Insert emoji"
                    >
                        😊
                    </button>
                    {showEmojiPicker && (
                        <div ref={emojiPickerRef} className="emoji-picker-container">
                            <Picker
                                data={data}
                                onEmojiSelect={handleEmojiSelect}
                                theme="auto"
                                previewPosition="none"
                                skinTonePosition="none"
                                maxFrequentRows={2}
                            />
                        </div>
                    )}
                </div>

                <div className="toolbar-separator" />

                <div className="toolbar-group">
                    <button
                        type="button"
                        className={`toolbar-btn ${editor.isActive('bold') ? 'active' : ''}`}
                        onClick={() => editor.chain().focus().toggleBold().run()}
                        title="Bold"
                    >
                        <strong>B</strong>
                    </button>
                    <button
                        type="button"
                        className={`toolbar-btn ${editor.isActive('italic') ? 'active' : ''}`}
                        onClick={() => editor.chain().focus().toggleItalic().run()}
                        title="Italic"
                    >
                        <em>I</em>
                    </button>
                    <button
                        type="button"
                        className={`toolbar-btn ${editor.isActive('underline') ? 'active' : ''}`}
                        onClick={() => editor.chain().focus().toggleUnderline().run()}
                        title="Underline"
                    >
                        <u>U</u>
                    </button>
                </div>

                <div className="toolbar-separator" />

                <div className="toolbar-group">
                    <button
                        type="button"
                        className={`toolbar-btn ${editor.isActive({ textAlign: 'left' }) ? 'active' : ''}`}
                        onClick={() => editor.chain().focus().setTextAlign('left').run()}
                        title="Align left"
                    >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <line x1="3" y1="6" x2="21" y2="6" />
                            <line x1="3" y1="12" x2="15" y2="12" />
                            <line x1="3" y1="18" x2="18" y2="18" />
                        </svg>
                    </button>
                    <button
                        type="button"
                        className={`toolbar-btn ${editor.isActive({ textAlign: 'center' }) ? 'active' : ''}`}
                        onClick={() => editor.chain().focus().setTextAlign('center').run()}
                        title="Align center"
                    >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <line x1="3" y1="6" x2="21" y2="6" />
                            <line x1="6" y1="12" x2="18" y2="12" />
                            <line x1="4" y1="18" x2="20" y2="18" />
                        </svg>
                    </button>
                    <button
                        type="button"
                        className={`toolbar-btn ${editor.isActive({ textAlign: 'right' }) ? 'active' : ''}`}
                        onClick={() => editor.chain().focus().setTextAlign('right').run()}
                        title="Align right"
                    >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <line x1="3" y1="6" x2="21" y2="6" />
                            <line x1="9" y1="12" x2="21" y2="12" />
                            <line x1="6" y1="18" x2="21" y2="18" />
                        </svg>
                    </button>
                </div>

                <div className="toolbar-separator" />

                <div className="toolbar-group">
                    <button
                        type="button"
                        className={`toolbar-btn ${editor.isActive('blockquote') ? 'active' : ''}`}
                        onClick={() => editor.chain().focus().toggleBlockquote().run()}
                        title="Quote"
                    >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V21z" />
                            <path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3z" />
                        </svg>
                    </button>
                    <button
                        type="button"
                        className={`toolbar-btn ${editor.isActive('bulletList') ? 'active' : ''}`}
                        onClick={() => editor.chain().focus().toggleBulletList().run()}
                        title="Bullet list"
                    >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <line x1="9" y1="6" x2="20" y2="6" />
                            <line x1="9" y1="12" x2="20" y2="12" />
                            <line x1="9" y1="18" x2="20" y2="18" />
                            <circle cx="4" cy="6" r="1.5" fill="currentColor" />
                            <circle cx="4" cy="12" r="1.5" fill="currentColor" />
                            <circle cx="4" cy="18" r="1.5" fill="currentColor" />
                        </svg>
                    </button>
                </div>
            </div>

            <EditorContent editor={editor} className="editor-content" />
        </div>
    );
}
