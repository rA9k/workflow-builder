// Load jsPlumb from CDN and initialize
(function () {
    // Check if jsPlumb is already loaded
    if (window.jsPlumb) {
        console.log('jsPlumb already loaded');
        if (window.workflowConnections && !window.workflowConnections.jsPlumbInstance) {
            window.workflowConnections.initialize();
        }
        return;
    }
    window.workflowConnections = {
        initialize: function () {
            if (typeof jsPlumb === 'undefined') {
                console.error("jsPlumb library not loaded!");
                alert("Required library (jsPlumb) is missing. Please check your installation.");
                return;
            }
        },
    };

    console.log('Loading jsPlumb from CDN...');
    // const script = document.createElement('script');
    // script.src = 'https://cdnjs.cloudflare.com/ajax/libs/jsPlumb/2.15.6/js/jsplumb.min.js';
    // script.integrity = 'sha512-4v3WV8+xSIpJVNuNkUYutJ+/iNIL6a0ixOIJvbUUUDuLOEdQi0mJxXpNmXy9ogqGQRNZ+fSzfQR0XOsXvfYYqA==';
    // script.crossOrigin = 'anonymous';

    script.onload = function () {
        console.log('jsPlumb loaded successfully');
        // Initialize after a short delay to ensure DOM is ready
        setTimeout(() => {
            if (window.workflowConnections) {
                window.workflowConnections.initialize();
            }
        }, 500);
    };

    document.head.appendChild(script);
})();
