// Theme-specific JavaScript for responsive behavior
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
});
