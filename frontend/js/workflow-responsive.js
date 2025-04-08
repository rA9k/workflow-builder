// Wait for the document to be fully loaded
window.addEventListener('DOMContentLoaded', () => {
    // Register service worker for offline support
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js')
        .then(registration => {
          console.log('ServiceWorker registration successful with scope: ', registration.scope);
        })
        .catch(err => {
          console.log('ServiceWorker registration failed: ', err);
        });
    }

    // Initialize workflow progress
    initWorkflowProgress();
  
    // Add responsive behavior to document viewers
    setupDocumentViewers();
});

function initWorkflowProgress() {
    const progressBars = document.querySelectorAll('.workflow-progress-fill');
    progressBars.forEach(bar => {
      // Get progress percentage from data attribute
      const progress = bar.dataset.progress || 0;
    
      // Set the width of the progress bar
      if (window.innerWidth > 768) {
        bar.style.width = `${progress}%`;
      } else {
        bar.style.height = `${progress}%`;
        bar.style.setProperty('--progress-height', `${progress}%`);
      }
    
      // Update stage indicators
      const stages = document.querySelectorAll('.stage-indicator');
      const currentStage = Math.ceil((progress / 100) * stages.length);
    
      stages.forEach((stage, index) => {
        if (index < currentStage - 1) {
          stage.classList.add('completed');
          stage.classList.remove('active');
        } else if (index === currentStage - 1) {
          stage.classList.add('active');
          stage.classList.remove('completed');
        } else {
          stage.classList.remove('active', 'completed');
        }
      });
    });
}

function setupDocumentViewers() {
    // Make document viewer dialogs responsive
    const viewerDialogs = document.querySelectorAll('.document-viewer-dialog');
    viewerDialogs.forEach(dialog => {
      if (window.innerWidth < 768) {
        dialog.style.width = '95%';
        dialog.style.height = '70vh';
      }
    });
}

// Function to update workflow progress dynamically
window.updateWorkflowProgress = function(progressPercentage) {
    const progressBar = document.querySelector('.workflow-progress-fill');
    if (progressBar) {
      progressBar.dataset.progress = progressPercentage;
      initWorkflowProgress();
    }
};
