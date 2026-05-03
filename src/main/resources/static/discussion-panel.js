/**
 * DiscussionPanel - Component for topic discussions
 * Enables users to discuss learning resources
 */
class DiscussionPanel {
    constructor(containerId) {
        this.container = document.getElementById(containerId);
        this.roomId = null;
        this.messages = [];
    }
    /**
     * Initialize discussion for topic
     */
    init(topicName, userId) {
        this.topicName = topicName;
        this.userId = userId;
        this.getOrCreateRoom();
        this.setupAutoRefresh();
    }
    /**
     * Get or create discussion room
     */
    getOrCreateRoom() {
        fetch(`/api/discussion/rooms/topic?name=${encodeURIComponent(this.topicName)}`, {
            headers: {
                'Authorization': `Bearer ${this.getToken()}`
            }
        })
        .then(r => r.json())
        .then(room => {
            this.roomId = room.id;
            this.loadMessages();
            this.render();
        })
        .catch(error => console.error('Error getting room:', error));
    }
    /**
     * Load messages from room
     */
    loadMessages() {
        if (!this.roomId) return;
        fetch(`/api/discussion/rooms/${this.roomId}/messages?limit=30`, {
            headers: {
                'Authorization': `Bearer ${this.getToken()}`
            }
        })
        .then(r => r.json())
        .then(messages => {
            this.messages = messages;
            this.render();
        })
        .catch(error => console.error('Error loading messages:', error));
    }
    /**
     * Render discussion panel
     */
    render() {
        const html = `
            <div class="discussion-panel">
                <div class="discussion-header">
                    <h3>💬 ${this.topicName}</h3>
                    <p class="subtitle">Discussion & Experience Sharing</p>
                </div>
                <div class="messages-container" id="messages-list">
                    ${this.messages.length > 0
                        ? this.messages.map(msg => this.renderMessage(msg)).join('')
                        : '<p class="empty">No messages yet. Be the first!</p>'
                    }
                </div>
                <div class="message-input-area">
                    <textarea id="message-input"
                              placeholder="Share your thoughts..."></textarea>
                    <button class="btn-send" id="send-btn">
                        📤 Send
                    </button>
                </div>
            </div>
        `;
        this.container.innerHTML = html;
        this.attachEventListeners();
    }
    /**
     * Render single message
     */
    renderMessage(msg) {
        const timeAgo = this.getTimeAgo(new Date(msg.createdAt));
        return `
            <div class="message" data-message-id="${msg.id}">
                <div class="message-header">
                    <span class="username">👤 ${msg.username}</span>
                    <span class="time">${timeAgo}</span>
                </div>
                <div class="message-content">
                    ${msg.content}
                </div>
                <div class="message-actions">
                    <button class="btn-like" data-message-id="${msg.id}">
                        👍 ${msg.likesCount || 0}
                    </button>
                </div>
            </div>
        `;
    }
    /**
     * Send message
     */
    attachEventListeners() {
        document.getElementById('send-btn').addEventListener('click', () => {
            const content = document.getElementById('message-input').value.trim();
            if (content) {
                this.sendMessage(content);
            }
        });
        document.querySelectorAll('.btn-like').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const messageId = e.target.dataset.messageId;
                this.likeMessage(messageId);
            });
        });
    }
    /**
     * Send message to room
     */
    sendMessage(content) {
        fetch(`/api/discussion/rooms/${this.roomId}/messages`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${this.getToken()}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ content })
        })
        .then(r => r.json())
        .then(msg => {
            document.getElementById('message-input').value = '';
            this.messages.unshift(msg);
            this.render();
        })
        .catch(error => console.error('Error sending message:', error));
    }
    /**
     * Like message
     */
    likeMessage(messageId) {
        fetch(`/api/discussion/messages/${messageId}/like`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${this.getToken()}`
            }
        })
        .then(() => this.loadMessages())
        .catch(error => console.error('Error liking message:', error));
    }
    /**
     * Setup auto-refresh
     */
    setupAutoRefresh() {
        setInterval(() => this.loadMessages(), 10000);
    }
    /**
     * Get token
     */
    getToken() {
        return localStorage.getItem('authToken') || '';
    }
    /**
     * Get time ago string
     */
    getTimeAgo(date) {
        const seconds = Math.floor((new Date() - date) / 1000);
        let interval = seconds / 31536000;
        if (interval > 1) return Math.floor(interval) + ' years ago';
        interval = seconds / 2592000;
        if (interval > 1) return Math.floor(interval) + ' months ago';
        interval = seconds / 86400;
        if (interval > 1) return Math.floor(interval) + ' days ago';
        interval = seconds / 3600;
        if (interval > 1) return Math.floor(interval) + ' hours ago';
        interval = seconds / 60;
        if (interval > 1) return Math.floor(interval) + ' minutes ago';
        return Math.floor(seconds) + ' seconds ago';
    }
}
document.addEventListener('DOMContentLoaded', () => {
    const panel = new DiscussionPanel('discussion-panel');
    const topicName = document.querySelector('[data-topic]')?.dataset.topic;
    const userId = document.querySelector('[data-user-id]')?.dataset.userId;
    if (topicName && userId) {
        panel.init(topicName, userId);
    }
});