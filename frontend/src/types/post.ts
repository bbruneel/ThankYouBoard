export interface Post {
    id: string;
    authorName: string;
    messageText?: string | null;
    giphyUrl?: string | null;
    uploadedImageUrl?: string | null;
    createdAt: string;

    // Present only when a post is created anonymously (used to authorize edits/deletes).
    editDeleteToken?: string | null;
}
