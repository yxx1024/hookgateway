/**
 * HookUI - Webhook Gateway 统一 UI 工具库
 * 提供美化的 Alert, Confirm 和 Toast 功能。
 */
window.HookUI = (function() {
    // 动态创建并注入 CSS 动画
    const style = document.createElement('style');
    style.textContent = `
        @keyframes modal-in {
            from { opacity: 0; transform: scale(0.95) translateY(10px); }
            to { opacity: 1; transform: scale(1) translateY(0); }
        }
        @keyframes modal-out {
            from { opacity: 1; transform: scale(1) translateY(0); }
            to { opacity: 0; transform: scale(0.95) translateY(10px); }
        }
        .animate-modal-in { animation: modal-in 0.2s ease-out forwards; }
        .animate-modal-out { animation: modal-out 0.15s ease-in forwards; }
    `;
    document.head.appendChild(style);

    function createOverlay() {
        const overlay = document.createElement('div');
        overlay.className = 'fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm transition-opacity duration-300';
        return overlay;
    }

    function createModal(title, message, isConfirm, callback) {
        const overlay = createOverlay();
        const modal = document.createElement('div');
        modal.className = 'bg-white rounded-3xl shadow-2xl border border-slate-100 w-full max-w-sm overflow-hidden animate-modal-in';
        
        const contentHtml = `
            <div class="px-6 pt-8 pb-6 text-center">
                <div class="inline-flex items-center justify-center w-12 h-12 ${isConfirm ? 'bg-amber-100 text-amber-600' : 'bg-blue-100 text-blue-600'} rounded-full mb-4">
                    ${isConfirm ? 
                        '<svg class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/></svg>' :
                        '<svg class="w-6 h-6" fill="none" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd"/></svg>'
                    }
                </div>
                <h3 class="text-xl font-bold text-slate-800 mb-2">${title || (isConfirm ? '确认执行?' : '提示')}</h3>
                <p class="text-slate-500 text-sm leading-relaxed">${message}</p>
            </div>
            <div class="px-6 py-4 bg-slate-50 flex gap-3">
                ${isConfirm ? `
                    <button id="hook-modal-cancel" class="flex-1 px-4 py-2.5 bg-white border border-slate-200 text-slate-600 font-bold rounded-xl hover:bg-slate-100 transition-colors">取消</button>
                    <button id="hook-modal-ok" class="flex-1 px-4 py-2.5 bg-slate-800 text-white font-bold rounded-xl hover:bg-slate-900 shadow-md transition-all active:scale-95">确定</button>
                ` : `
                    <button id="hook-modal-ok" class="w-full px-4 py-2.5 bg-blue-600 text-white font-bold rounded-xl hover:bg-blue-700 shadow-md transition-all active:scale-95">知道了</button>
                `}
            </div>
        `;
        
        modal.innerHTML = contentHtml;
        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        const close = (result) => {
            modal.classList.replace('animate-modal-in', 'animate-modal-out');
            overlay.classList.add('opacity-0');
            setTimeout(() => {
                document.body.removeChild(overlay);
                if (callback) callback(result);
            }, 150);
        };

        modal.querySelector('#hook-modal-ok').onclick = () => close(true);
        if (isConfirm) {
            modal.querySelector('#hook-modal-cancel').onclick = () => close(false);
        }
        
        // 点击背景关闭 (Confirm 模式默认不点击背景关闭，防止误操作)
        if (!isConfirm) {
            overlay.onclick = (e) => { if(e.target === overlay) close(true); };
        }
    }

    return {
        alert: function(message, title) {
            return new Promise((resolve) => {
                createModal(title, message, false, resolve);
            });
        },
        confirm: function(message, title) {
            return new Promise((resolve) => {
                createModal(title, message, true, resolve);
            });
        },
        toast: function(message, type = 'success') {
            // 如果页面已有 toast 容器则复用，否则创建
            let toastContainer = document.getElementById('hook-ui-toast-container');
            if (!toastContainer) {
                toastContainer = document.createElement('div');
                toastContainer.id = 'hook-ui-toast-container';
                toastContainer.className = 'fixed bottom-6 right-6 z-[200] space-y-3';
                document.body.appendChild(toastContainer);
            }

            const toast = document.createElement('div');
            toast.className = 'bg-white border border-slate-200 shadow-2xl rounded-2xl px-6 py-4 flex items-center gap-3 animate-modal-in min-w-[240px]';
            
            const iconColor = type === 'success' ? 'text-emerald-500 bg-emerald-50' : (type === 'error' ? 'text-rose-500 bg-rose-50' : 'text-blue-500 bg-blue-50');
            const icon = type === 'success' ? 
                '<svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/></svg>' :
                '<svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>';

            toast.innerHTML = `
                <div class="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${iconColor}">
                    ${icon}
                </div>
                <span class="text-sm font-bold text-slate-700">${message}</span>
            `;

            toastContainer.appendChild(toast);

            setTimeout(() => {
                toast.classList.replace('animate-modal-in', 'animate-modal-out');
                toast.classList.add('opacity-0');
                setTimeout(() => {
                    if (toast.parentNode) toast.parentNode.removeChild(toast);
                }, 200);
            }, 3000);
        }
    };
})();

// 兼容全局函数调用简化 (可选)
window.showAlert = (msg, title) => HookUI.alert(msg, title);
window.showConfirm = (msg, title) => HookUI.confirm(msg, title);
window.showToast = (msg, type) => HookUI.toast(msg, type);
