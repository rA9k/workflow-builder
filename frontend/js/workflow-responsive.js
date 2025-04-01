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

    // Handle properties panel in mobile view
    const propertiesPanel = document.querySelector('.properties-panel');
    if (propertiesPanel) {
        // On mobile, when properties panel opens, add a semi-transparent overlay
        // to make it clear it's a modal
        const handlePropertiesPanelVisibility = () => {
            if (window.innerWidth <= 768 && propertiesPanel.classList.contains('open')) {
                // Create overlay if it doesn't exist
                let overlay = document.querySelector('.mobile-properties-overlay');
                if (!overlay) {
                    overlay = document.createElement('div');
                    overlay.className = 'mobile-properties-overlay';
                    overlay.style.cssText = `
                        position: fixed;
                        top: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        background: rgba(0,0,0,0.5);
                        z-index: 2999;
                    `;
                    document.body.appendChild(overlay);
                    
                    // Close properties panel when clicking overlay
                    overlay.addEventListener('click', () => {
                        propertiesPanel.classList.remove('open');
                        propertiesPanel.style.right = '-100%';
                        overlay.remove();
                    });
                }
            }
        };
        
        // Watch for class changes on properties panel
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                if (mutation.attributeName === 'class') {
                    handlePropertiesPanelVisibility();
                }
            });
        });
        
        observer.observe(propertiesPanel, { attributes: true });
    }
    
    // Fix toolbar buttons on resize
    const adjustToolbarButtons = () => {
        const btnLayout = document.querySelector('.responsive-button-layout');
        if (btnLayout) {
            if (window.innerWidth <= 768) {
                btnLayout.style.flexDirection = 'column';
                Array.from(btnLayout.children).forEach(btn => {
                    btn.style.width = '100%';
                    btn.style.margin = '5px 0';
                });
            } else {
                btnLayout.style.flexDirection = 'row';
                Array.from(btnLayout.children).forEach(btn => {
                    btn.style.width = 'auto';
                    btn.style.margin = '0 5px';
                });
            }
        }
    };
    
    window.addEventListener('resize', adjustToolbarButtons);
    adjustToolbarButtons(); // Run once on load

    // Fix for properties panel close button
    document.addEventListener('click', (event) => {
        if (event.target.closest('.properties-close-btn') || 
            event.target.closest('.properties-close-btn vaadin-icon')) {
            
            // Find the properties panel
            const propertiesPanel = document.querySelector('.properties-panel');
            if (propertiesPanel) {
                // Remove the open class
                propertiesPanel.classList.remove('open');
                
                // Set the right style to hide it
                propertiesPanel.style.right = '-300px';
                
                // Remove margin from canvas
                const canvas = document.querySelector('.workflow-canvas');
                if (canvas) {
                    canvas.style.marginRight = '0';
                }
                
                // Remove any mobile overlay
                const overlay = document.querySelector('.mobile-properties-overlay');
                if (overlay) {
                    overlay.remove();
                }
            }
        }
    }, true);
});
