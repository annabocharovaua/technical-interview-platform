/**
 * AnalyticsDashboard - Component for viewing resource analytics
 * Shows statistics about resource usage and popularity
 */
class AnalyticsDashboard {
    constructor(containerId) {
        this.container = document.getElementById(containerId);
        this.data = {};
    }
    /**
     * Initialize dashboard
     */
    init() {
        this.loadTopViewed();
        this.loadMostDiscussed();
        this.render();
    }
    /**
     * Load top viewed resources
     */
    loadTopViewed() {
        fetch('/api/analytics/top-viewed?limit=10')
            .then(r => r.json())
            .then(data => {
                this.data.topViewed = data;
                this.render();
            })
            .catch(error => console.error('Error loading top viewed:', error));
    }
    /**
     * Load most discussed resources
     */
    loadMostDiscussed() {
        fetch('/api/analytics/most-discussed?limit=10')
            .then(r => r.json())
            .then(data => {
                this.data.mostDiscussed = data;
                this.render();
            })
            .catch(error => console.error('Error loading most discussed:', error));
    }
    /**
     * Render dashboard
     */
    render() {
        const html = `
            <div class="analytics-dashboard">
                <h2>📊 Resource Statistics</h2>
                <div class="dashboard-grid">
                    <div class="dashboard-section">
                        <h3>🔥 Top Viewed</h3>
                        <div class="analytics-list">
                            ${this.data.topViewed
                                ? this.renderTopViewed()
                                : '<p>Loading...</p>'
                            }
                        </div>
                    </div>
                    <div class="dashboard-section">
                        <h3>💬 Most Discussed</h3>
                        <div class="analytics-list">
                            ${this.data.mostDiscussed
                                ? this.renderMostDiscussed()
                                : '<p>Loading...</p>'
                            }
                        </div>
                    </div>
                </div>
                <div class="dashboard-section full-width">
                    <h3>📈 Popularity by Topic</h3>
                    <div id="topics-chart" class="chart-container">
                        <p>Chart is loading...</p>
                    </div>
                </div>
            </div>
        `;
        this.container.innerHTML = html;
    }
    /**
     * Render top viewed section
     */
    renderTopViewed() {
        if (!this.data.topViewed || this.data.topViewed.length === 0) {
            return '<p class="empty">No data</p>';
        }
        return `
            <table class="analytics-table">
                <tbody>
                    ${this.data.topViewed.map((item, idx) => `
                        <tr>
                            <td class="rank">#${idx + 1}</td>
                            <td class="count">
                                👁️ ${item.viewCount}
                            </td>
                            <td class="label">
                                <button class="link-btn"
                                        data-resource-id="${item.resourceId}">
                                    View Resource
                                </button>
                            </td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `;
    }
    /**
     * Render most discussed section
     */
    renderMostDiscussed() {
        if (!this.data.mostDiscussed || this.data.mostDiscussed.length === 0) {
            return '<p class="empty">No data</p>';
        }
        return `
            <table class="analytics-table">
                <tbody>
                    ${this.data.mostDiscussed.map((item, idx) => `
                        <tr>
                            <td class="rank">#${idx + 1}</td>
                            <td class="count">
                                💬 ${item.discussionCount}
                            </td>
                            <td class="label">
                                ${item.title}
                            </td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `;
    }
    /**
     * Get resource metrics
     */
    getResourceMetrics(resourceId) {
        fetch(`/api/analytics/resource/${resourceId}/metrics`)
            .then(r => r.json())
            .then(metrics => {
                this.showMetricsModal(metrics);
            })
            .catch(error => console.error('Error loading metrics:', error));
    }
    /**
     * Show metrics modal
     */
    showMetricsModal(metrics) {
        const modal = document.createElement('div');
        modal.className = 'modal metrics-modal';
        modal.innerHTML = `
            <div class="modal-content">
                <h3>📊 Resource Statistics</h3>
                <div class="metrics-grid">
                    <div class="metric-card">
                        <span class="metric-label">Views</span>
                        <span class="metric-value">👁️ ${metrics.views}</span>
                    </div>
                    <div class="metric-card">
                        <span class="metric-label">Clicks</span>
                        <span class="metric-value">🔗 ${metrics.clicks}</span>
                    </div>
                    <div class="metric-card">
                        <span class="metric-label">Ratings</span>
                        <span class="metric-value">⭐ ${metrics.ratings}</span>
                    </div>
                    <div class="metric-card">
                        <span class="metric-label">Recommendations</span>
                        <span class="metric-value">💡 ${metrics.recommendations}</span>
                    </div>
                    <div class="metric-card full-width">
                        <span class="metric-label">Engagement Score</span>
                        <span class="metric-value">${metrics.engagementScore}/100</span>
                        <div class="progress-bar">
                            <div class="progress"
                                 style="width: ${metrics.engagementScore}%"></div>
                        </div>
                    </div>
                </div>
                <button class="btn-close" onclick="this.closest('.modal').remove()">
                    Close
                </button>
            </div>
        `;
        document.body.appendChild(modal);
    }
    /**
     * Setup event listeners
     */
    attachEventListeners() {
        document.querySelectorAll('.link-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const resourceId = e.target.dataset.resourceId;
                this.getResourceMetrics(resourceId);
            });
        });
    }
}
document.addEventListener('DOMContentLoaded', () => {
    const dashboard = new AnalyticsDashboard('analytics-dashboard');
    dashboard.init();
    setInterval(() => dashboard.init(), 5 * 60 * 1000);
});