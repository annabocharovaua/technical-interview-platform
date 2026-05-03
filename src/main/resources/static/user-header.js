console.warn('user-header.js is completely disabled. Using global header from app.html only.');
(function() {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', hideOldHeaders);
    } else {
        hideOldHeaders();
    }
    function hideOldHeaders() {
        const oldHeaders = document.querySelectorAll('#globalUserHeader');
        oldHeaders.forEach(el => {
            if (el && el.parentElement && el.parentElement.id !== 'app') {
                el.style.display = 'none';
            }
        });
    }
})();
window.logoutUser = window.logoutUser || function() {
    localStorage.removeItem('currentUser');
    if (window.navigateTo) window.navigateTo('/home');
    else window.location.href = '/home';
};
window.setCurrentUser = window.setCurrentUser || function(user) {
    localStorage.setItem('currentUser', JSON.stringify(user));
};