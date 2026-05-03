/**
 * Anti-Cheat Manager
 * - Fullscreen enforcement
 * - Copy/Paste detection & blocking
 * - Violation logging
 */
class AntiCheatManager {
    constructor(options = {}) {
        this.enabled = options.enabled || false;
        this.blockCopyPaste = options.blockCopyPaste !== false;
        this.enforceFullscreen = options.enforceFullscreen !== false;
        this.mode = options.mode || 'full';
        this.onViolation = options.onViolation || (() => {});
        this.violations = [];
        this.fullscreenExitCount = 0;
        this.copyAttempts = 0;
        this.pasteAttempts = 0;
        this.isActive = false;
        this._boundFullscreenChange = this.handleFullscreenChange.bind(this);
        this._boundCopy = this.handleCopy.bind(this);
        this._boundPaste = this.handlePaste.bind(this);
        this._boundCut = this.handleCut.bind(this);
        this._boundKeyDown = this.handleKeyDown.bind(this);
        this._boundContextMenu = this.handleContextMenu.bind(this);
    }
    async start() {
        if (!this.enabled) {
            console.log('🛡️ Anti-cheat disabled by user');
            return;
        }
        if (this.isActive) return;
        this.isActive = true;
        console.log(`🛡️ Anti-cheat activated (mode: ${this.mode})`);
        if (this.enforceFullscreen) {
            await this.requestFullscreen();
            document.addEventListener('fullscreenchange', this._boundFullscreenChange);
            document.addEventListener('webkitfullscreenchange', this._boundFullscreenChange);
        }
        if (this.blockCopyPaste && this.mode !== 'voice') {
            document.addEventListener('copy', this._boundCopy);
            document.addEventListener('paste', this._boundPaste);
            document.addEventListener('cut', this._boundCut);
            document.addEventListener('keydown', this._boundKeyDown);
            document.addEventListener('contextmenu', this._boundContextMenu);
        }
    }
    /**
     * Stop anti-cheat
     */
    stop() {
        if (!this.isActive) return;
        this.isActive = false;
        console.log('🛡️ Anti-cheat stopping...');
        document.removeEventListener('fullscreenchange', this._boundFullscreenChange);
        document.removeEventListener('webkitfullscreenchange', this._boundFullscreenChange);
        document.removeEventListener('copy', this._boundCopy);
        document.removeEventListener('paste', this._boundPaste);
        document.removeEventListener('cut', this._boundCut);
        document.removeEventListener('keydown', this._boundKeyDown);
        document.removeEventListener('contextmenu', this._boundContextMenu);
        this.hideFullscreenOverlay();
        if (document.fullscreenElement || document.webkitFullscreenElement) {
            document.exitFullscreen().catch(() => {});
        }
        console.log('🛡️ Anti-cheat deactivated');
    }
    async requestFullscreen() {
        const el = document.documentElement;
        try {
            if (el.requestFullscreen) {
                await el.requestFullscreen();
            } else if (el.webkitRequestFullscreen) {
                const result = el.webkitRequestFullscreen();
                if (result && typeof result.then === 'function') {
                    await result;
                }
            } else {
                throw new Error('Fullscreen API not supported');
            }
            console.log('✅ Entered fullscreen');
            return true;
        } catch (err) {
            console.error('❌ Failed to enter fullscreen:', err.name, err.message);
            this.logViolation('FULLSCREEN_DENIED',
                `User denied fullscreen: ${err.name || 'Unknown'} - ${err.message || 'No message'}`);
            throw err;
        }
    }
    handleFullscreenChange() {
        if (!this.isActive) return;
        const isFullscreen = !!(document.fullscreenElement || document.webkitFullscreenElement);
        if (!isFullscreen) {
            this.fullscreenExitCount++;
            this.logViolation('FULLSCREEN_EXIT', `User exited fullscreen (attempt #${this.fullscreenExitCount})`);
            setTimeout(() => {
                if (this.isActive && !document.fullscreenElement && !document.webkitFullscreenElement) {
                    this.showFullscreenOverlay();
                }
            }, 100);
        } else {
            this.hideFullscreenOverlay();
        }
    }
     /**
      * Shows full-screen overlay with a button to return to fullscreen.
      * Unlike confirm(), clicking the button creates a valid user gesture.
      */
    showFullscreenOverlay() {
        if (document.getElementById('antiCheatFullscreenOverlay')) return;
        const overlay = document.createElement('div');
        overlay.id = 'antiCheatFullscreenOverlay';
        overlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background: rgba(0, 0, 0, 0.92);
        backdrop-filter: blur(8px);
        z-index: 2147483647;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-direction: column;
        padding: 40px;
        animation: acFadeIn 0.3s ease;
    `;
        if (!document.getElementById('antiCheatOverlayStyles')) {
            const style = document.createElement('style');
            style.id = 'antiCheatOverlayStyles';
            style.textContent = `
            @keyframes acFadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            @keyframes acPulse {
                0%, 100% { transform: scale(1); }
                50% { transform: scale(1.05); }
            }
            #antiCheatReturnBtn:hover {
                background: linear-gradient(135deg, #ff6b7a 0%, #e03c4e 100%) !important;
                transform: translateY(-2px);
                box-shadow: 0 12px 40px rgba(220, 53, 69, 0.5) !important;
            }
            #antiCheatReturnBtn:active {
                transform: translateY(0);
            }
        `;
            document.head.appendChild(style);
        }
        overlay.innerHTML = `
        <div style="
            font-size: 80px;
            margin-bottom: 20px;
            animation: acPulse 2s ease-in-out infinite;
        ">⚠️</div>
        <h1 style="
            color: #ff6b7a;
            font-size: 32px;
            margin: 0 0 16px 0;
            text-align: center;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            font-weight: 700;
        ">Violation Detected</h1>
        <p style="
            color: rgba(255, 255, 255, 0.9);
            font-size: 18px;
            text-align: center;
            max-width: 500px;
            line-height: 1.6;
            margin: 0 0 12px 0;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
        ">You have exited fullscreen mode.<br>
        This violation #${this.fullscreenExitCount} has been recorded in the report.</p>
        <p style="
            color: rgba(255, 255, 255, 0.6);
            font-size: 14px;
            text-align: center;
            margin: 0 0 32px 0;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
        ">Click the button below to return to fullscreen<br>and continue the interview.</p>
        <button id="antiCheatReturnBtn" style="
            padding: 18px 48px;
            background: linear-gradient(135deg, #ff5566 0%, #dc3545 100%);
            color: white;
            border: none;
            border-radius: 14px;
            font-size: 17px;
            font-weight: 600;
            cursor: pointer;
            box-shadow: 0 8px 24px rgba(220, 53, 69, 0.4);
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            transition: all 0.2s ease;
            letter-spacing: 0.3px;
        ">
            🖥️ Return to Fullscreen
        </button>
        <p style="
            color: rgba(255, 255, 255, 0.4);
            font-size: 12px;
            text-align: center;
            margin: 24px 0 0 0;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
        ">Total violations:${this.violations.length}</p>
    `;
        document.body.appendChild(overlay);
        const btn = document.getElementById('antiCheatReturnBtn');
        btn.addEventListener('click', async () => {
            btn.disabled = true;
            btn.textContent = '⏳ Returning...';
            btn.style.opacity = '0.7';
            try {
                await this.requestFullscreen();
            } catch (err) {
                btn.disabled = false;
                btn.textContent = '🖥️ Return to Fullscreen';
                btn.style.opacity = '1';
                console.error('❌ Failed to return to fullscreen:', err);
                this.showFlash('Failed to return to fullscreen. Please try again.');
            }
        });
        setTimeout(() => btn.focus(), 100);
    }
    /**
     * Hides fullscreen overlay
     */
    hideFullscreenOverlay() {
        const overlay = document.getElementById('antiCheatFullscreenOverlay');
        if (overlay) {
            overlay.style.opacity = '0';
            overlay.style.transition = 'opacity 0.2s ease';
            setTimeout(() => overlay.remove(), 200);
        }
    }
    handleCopy(e) {
        e.preventDefault();
        this.copyAttempts++;
        this.logViolation('COPY_ATTEMPT', `Copy attempt #${this.copyAttempts}`);
        this.showFlash('Copying is not allowed');
    }
    handlePaste(e) {
        if (this.mode !== 'coding') {
            e.preventDefault();
        }
        this.pasteAttempts++;
        this.logViolation('PASTE_ATTEMPT', `Paste attempt #${this.pasteAttempts}`);
        this.showFlash('Pasting is forbidden');
    }
    handleCut(e) {
        e.preventDefault();
        this.logViolation('CUT_ATTEMPT', 'Cut attempt');
        this.showFlash('Cutting is not allowed');
    }
    handleKeyDown(e) {
        const isCtrl = e.ctrlKey || e.metaKey;
        if (e.key === 'F12' || (isCtrl && e.shiftKey && ['I', 'J', 'C'].includes(e.key))) {
            e.preventDefault();
            this.logViolation('DEVTOOLS_ATTEMPT', `Blocked DevTools shortcut: ${e.key}`);
            this.showFlash('DevTools blocked');
        }
    }
    handleContextMenu(e) {
        e.preventDefault();
        this.logViolation('CONTEXT_MENU', 'Right-click blocked');
    }
    logViolation(type, details) {
        const violation = {
            type,
            details,
            timestamp: new Date().toISOString()
        };
        this.violations.push(violation);
        console.warn('🚨 Violation:', violation);
        this.onViolation(violation);
    }
    showFlash(message) {
        let flash = document.getElementById('antiCheatFlash');
        if (!flash) {
            flash = document.createElement('div');
            flash.id = 'antiCheatFlash';
            flash.style.cssText = `
                position: fixed;
                top: 80px;
                left: 50%;
                transform: translateX(-50%);
                background: rgba(220, 53, 69, 0.95);
                color: white;
                padding: 12px 24px;
                border-radius: 10px;
                z-index: 99999;
                font-weight: 600;
                font-size: 14px;
                box-shadow: 0 4px 20px rgba(0,0,0,0.3);
                pointer-events: none;
                opacity: 0;
                transition: opacity 0.3s;
            `;
            document.body.appendChild(flash);
        }
        flash.textContent = '⚠️ ' + message;
        flash.style.opacity = '1';
        clearTimeout(flash._timer);
        flash._timer = setTimeout(() => { flash.style.opacity = '0'; }, 2000);
    }
    showWarning() {
        const warn = confirm(
            '⚠️ WARNING: You exited fullscreen mode!\n\n' +
            'This was recorded as a violation.\n' +
            'Click OK to return to fullscreen mode.'
        );
        if (warn && this.isActive) {
            this.requestFullscreen();
        }
    }
    getReport() {
        return {
            enabled: this.enabled,
            totalViolations: this.violations.length,
            fullscreenExits: this.fullscreenExitCount,
            copyAttempts: this.copyAttempts,
            pasteAttempts: this.pasteAttempts,
            violations: this.violations
        };
    }
}
window.AntiCheatManager = AntiCheatManager;