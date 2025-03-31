// Responsive behavior for workflow views
window.addEventListener('DOMContentLoaded', () => {
    // Handle responsive connector updates
    const updateResponsiveConnectors = () => {
        if (window.updateConnectors) {
            const canvas = document.querySelector('.workflow-canvas');
            if (canvas) {
                window.updateConnectors(canvas);
            }
        }
    };
    
    // Update connectors on resize
    window.addEventListener('resize', () => {
        updateResponsiveConnectors();
    });
    
    // Update connectors when sidebar is toggled
    const sidebarToggles = document.querySelectorAll('.mobile-toggle-btn');
    sidebarToggles.forEach(toggle => {
        toggle.addEventListener('click', () => {
            // Wait for the transition to complete
            setTimeout(updateResponsiveConnectors, 350);
        });
    });

    // Improve mobile horizontal scrolling
    const canvasContainer = document.querySelector('.canvas-container');
    if (canvasContainer) {
        // Check if we're on a mobile device
        if (window.innerWidth <= 768) {
            // Make sure horizontal scrolling is visible by adding a subtle indicator
            const scrollIndicator = document.createElement('div');
            scrollIndicator.className = 'scroll-indicator';
            scrollIndicator.innerHTML = '<span>←</span> Scroll <span>→</span>';
            scrollIndicator.style.cssText = `
                position: absolute;
                bottom: 10px;
                left: 50%;
                transform: translateX(-50%);
                background: rgba(33, 150, 243, 0.7);
                color: white;
                padding: 5px 10px;
                border-radius: 15px;
                font-size: 12px;
                z-index: 1000;
                pointer-events: none;
                opacity: 0;
                transition: opacity 0.3s;
            `;
            canvasContainer.appendChild(scrollIndicator);
            
            // Show the indicator briefly
            setTimeout(() => {
                scrollIndicator.style.opacity = '1';
                setTimeout(() => {
                    scrollIndicator.style.opacity = '0';
                    setTimeout(() => {
                        scrollIndicator.remove();
                    }, 1000);
                }, 3000);
            }, 500);
        }
    }
});
