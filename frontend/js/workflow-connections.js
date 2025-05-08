window.workflowConnections = (function () {
    let jsPlumbInstance;
    let removalMode = false;
    let editMode = true;
    let connectionUpdateTimeout = null;
    let repaintTimeout = null;
    let isRepainting = false;

    return {
        initialize: function () {
            console.log("Initializing jsPlumb instance");

            // If we already have an instance, reset it properly
            if (jsPlumbInstance) {
                try {
                    jsPlumbInstance.reset();
                } catch (e) {
                    console.error("Error resetting jsPlumb instance:", e);
                }
            }

            // Create a new instance
            jsPlumbInstance = jsPlumb.getInstance({
                Connector: ["Bezier", { curviness: 50 }],
                PaintStyle: { stroke: "#5c96bc", strokeWidth: 2 },
                EndpointStyle: { fill: "#5c96bc", radius: 7 },
                HoverPaintStyle: { stroke: "#1e8151", strokeWidth: 3 },
                ConnectionOverlays: [
                    ["Arrow", { location: 1, width: 10, length: 10, id: "arrow" }]
                ],
                Container: document.querySelector('.workflow-canvas'),
                // Improved drag options
                DragOptions: {
                    cursor: 'move',
                    zIndex: 2000,
                    grid: [5, 5],
                    containment: 'parent',
                    stop: function (params) {
                        // Force repaint of all connections when dragging stops
                        jsPlumbInstance.repaintEverything();
                    }
                }
            });

            // Store connections when they're created
            jsPlumbInstance.bind("connection", function (info) {
                // Update the server with the new connections
                this.updateServerConnections();
            }.bind(this));

            // Handle connection detachment
            jsPlumbInstance.bind("connectionDetached", function () {
                this.updateServerConnections();
            }.bind(this));

            // Add a MutationObserver to detect DOM changes
            this.setupMutationObserver();

            // Initialize endpoints for existing nodes
            setTimeout(() => {
                const nodes = document.querySelectorAll('.workflow-canvas [data-node-id]');
                nodes.forEach(node => {
                    const nodeId = node.getAttribute('data-node-id');
                    this.createEndpoints(node, nodeId);
                });
            }, 200);

            console.log("jsPlumb is initialized and ready");
            return jsPlumbInstance;
        },

        getAllConnections: function () {
            if (!jsPlumbInstance) return [];

            const connections = jsPlumbInstance.getAllConnections();
            return connections.map(conn => {
                // Get the actual DOM elements
                const sourceElement = conn.source;
                const targetElement = conn.target;

                // Extract the data-node-id attributes
                const sourceId = sourceElement.getAttribute('data-node-id');
                const targetId = targetElement.getAttribute('data-node-id');

                return {
                    source: sourceId,
                    target: targetId
                };
            });
        },

        setupMutationObserver: function () {
            // If we already have an observer, disconnect it
            if (this.observer) {
                this.observer.disconnect();
            }

            // Create a new observer
            this.observer = new MutationObserver((mutations) => {
                let needsRepaint = false;

                // Only check for relevant mutations
                for (const mutation of mutations) {
                    // If nodes were added or removed
                    if (mutation.type === 'childList') {
                        // Check if the added/removed nodes are relevant to jsPlumb
                        const relevantNodeChange = Array.from(mutation.addedNodes).some(node =>
                            node.nodeType === 1 && (
                                node.hasAttribute('data-node-id') ||
                                node.classList.contains('workflow-button')
                            )
                        ) || Array.from(mutation.removedNodes).some(node =>
                            node.nodeType === 1 && (
                                node.hasAttribute('data-node-id') ||
                                node.classList.contains('workflow-button')
                            )
                        );

                        if (relevantNodeChange) {
                            needsRepaint = true;
                            break;
                        }
                    }
                    // If attributes changed that might affect rendering
                    else if (mutation.type === 'attributes') {
                        if (mutation.attributeName === 'style' ||
                            mutation.attributeName === 'class') {
                            // Only repaint if the target is a node or endpoint
                            if (mutation.target.hasAttribute('data-node-id') ||
                                mutation.target.classList.contains('workflow-button') ||
                                mutation.target.classList.contains('jtk-endpoint')) {
                                needsRepaint = true;
                                break;
                            }
                        }
                    }
                }

                if (needsRepaint && !isRepainting) {
                    // Clear any existing timeout
                    if (repaintTimeout) {
                        clearTimeout(repaintTimeout);
                    }

                    // Delay the repaint to allow DOM to settle and prevent multiple repaints
                    repaintTimeout = setTimeout(() => {
                        this.debouncedRepaint();
                    }, 500);
                }
            });

            // Start observing the workflow canvas with more specific options
            const canvas = document.querySelector('.workflow-canvas');
            if (canvas) {
                this.observer.observe(canvas, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['style', 'class', 'data-node-id'] // Only watch relevant attributes
                });
            }
        },

        debouncedRepaint: function () {
            if (isRepainting) return; // Prevent reentrant calls

            isRepainting = true;
            console.log("Repainting jsPlumb elements");

            try {
                if (jsPlumbInstance) {
                    // Check if any nodes are missing endpoints
                    let needsEndpointRefresh = false;
                    const nodes = document.querySelectorAll('.workflow-canvas [data-node-id]');

                    nodes.forEach(node => {
                        const endpoints = jsPlumbInstance.getEndpoints(node);
                        if (!endpoints || endpoints.length < 2) {
                            needsEndpointRefresh = true;
                        }
                    });

                    // If any nodes are missing endpoints, do a full refresh
                    if (needsEndpointRefresh) {
                        this.refreshAllEndpoints();
                    } else {
                        // Otherwise just repaint
                        jsPlumbInstance.repaintEverything();
                    }
                }

                // Get current connections without triggering a server update
                const connections = this.getAllConnections();

                // Only update the server if there are actual connections
                if (connections.length > 0) {
                    // Use a separate timeout for server updates to prevent feedback loops
                    if (connectionUpdateTimeout) {
                        clearTimeout(connectionUpdateTimeout);
                    }

                    connectionUpdateTimeout = setTimeout(() => {
                        if (window.updateNodeConnections) {
                            window.updateNodeConnections(JSON.stringify(connections));
                        }
                    }, 300);
                }
            } finally {
                // Always reset the flag when done
                setTimeout(() => {
                    isRepainting = false;
                }, 1000); // Ensure at least 1 second between repaints
            }
        },

        repaintEverything: function () {
            if (!jsPlumbInstance) return;

            // Use the debounced version
            this.debouncedRepaint();
        },

        createEndpoints: function (element, nodeId) {
            console.log("Creating endpoints for node: " + nodeId);

            if (!jsPlumbInstance) {
                console.error("jsPlumb not initialized");
                jsPlumbInstance = this.initialize();
                setTimeout(() => this.createEndpoints(element, nodeId), 500);
                return;
            }

            if (!document.body.contains(element)) {
                console.warn("Element not in DOM, cannot create endpoints");
                return;
            }

            // IMPORTANT: Make sure the element has the right positioning
            element.style.position = 'absolute';

            // Get current position if already positioned
            const currentLeft = parseInt(element.style.left) || 0;
            const currentTop = parseInt(element.style.top) || 0;

            // Set position explicitly if not already set
            if (!element.style.left) {
                element.style.left = currentLeft + 'px';
            }
            if (!element.style.top) {
                element.style.top = currentTop + 'px';
            }

            // Create source endpoint (output)
            jsPlumbInstance.addEndpoint(element, {
                anchor: "Right",
                isSource: true,
                isTarget: false,
                maxConnections: -1,
                uniqueEndpoint: false,
                uuid: nodeId + "-source"
            });

            // Create target endpoint (input)
            jsPlumbInstance.addEndpoint(element, {
                anchor: "Left",
                isSource: false,
                isTarget: true,
                maxConnections: -1,
                uniqueEndpoint: false,
                uuid: nodeId + "-target"
            });

            console.log("Endpoints created successfully for node: " + nodeId);

            // Make the element draggable with jsPlumb if in edit mode
            if (editMode) {
                // Fixed draggable functionality with proper offset handling
                jsPlumbInstance.draggable(element, {
                    // Use the parent container for containment
                    containment: 'parent',
                    // Fine grid for smoother positioning
                    grid: [5, 5],
                    // Improved drag handling with proper offset calculation
                    start: function (params) {
                        // Store the initial position of the element
                        const rect = element.getBoundingClientRect();
                        // Calculate the offset between mouse position and element top-left corner
                        element._dragOffset = {
                            x: params.e.clientX - rect.left,
                            y: params.e.clientY - rect.top
                        };
                    },
                    drag: function (params) {
                        // Get the canvas element
                        const canvas = document.querySelector('.workflow-canvas');
                        const canvasRect = canvas.getBoundingClientRect();

                        // Calculate position relative to the canvas, accounting for the initial offset
                        let newLeft = params.e.clientX - canvasRect.left - element._dragOffset.x;
                        let newTop = params.e.clientY - canvasRect.top - element._dragOffset.y;

                        // Apply position constraints to keep element within canvas
                        const maxLeft = canvas.clientWidth - element.offsetWidth;
                        const maxTop = canvas.clientHeight - element.offsetHeight;

                        // Ensure the element stays within bounds
                        newLeft = Math.max(0, Math.min(newLeft, maxLeft));
                        newTop = Math.max(0, Math.min(newTop, maxTop));

                        // Apply the new position
                        element.style.left = newLeft + 'px';
                        element.style.top = newTop + 'px';

                        // Force connection redraw
                        jsPlumbInstance.revalidate(element);
                    },
                    stop: function () {
                        // Clean up after dragging
                        delete element._dragOffset;
                        // Ensure connections are properly updated
                        jsPlumbInstance.revalidate(element);
                        jsPlumbInstance.repaintEverything();
                    }
                });
            }
        },

        removeNodeConnections: function (nodeId) {
            if (!jsPlumbInstance) return;

            console.log("Removing connections for node: " + nodeId);

            try {
                // Find the element by nodeId
                const element = document.querySelector(`[data-node-id="${nodeId}"]`);

                if (element) {
                    // Remove all connections to/from this element
                    jsPlumbInstance.deleteConnectionsForElement(element);

                    // Remove all endpoints for this element
                    jsPlumbInstance.removeAllEndpoints(element);
                } else {
                    // If element not found, try to clean up by UUID
                    const sourceUUID = nodeId + "-source";
                    const targetUUID = nodeId + "-target";

                    // Remove endpoints by UUID
                    jsPlumbInstance.deleteEndpoint(sourceUUID);
                    jsPlumbInstance.deleteEndpoint(targetUUID);

                    // Clean up any connections that might reference these endpoints
                    const allConnections = jsPlumbInstance.getAllConnections();
                    for (let i = allConnections.length - 1; i >= 0; i--) {
                        const conn = allConnections[i];
                        const sourceId = conn.sourceId || (conn.source && conn.source.getAttribute("data-node-id"));
                        const targetId = conn.targetId || (conn.target && conn.target.getAttribute("data-node-id"));

                        if (sourceId === nodeId || targetId === nodeId) {
                            jsPlumbInstance.deleteConnection(conn);
                        }
                    }
                }

                // Force a complete repaint
                setTimeout(() => {
                    this.repaintEverything();
                    this.updateServerConnections();
                }, 50);
            } catch (e) {
                console.error("Error removing node connections:", e);
            }
        },

        refreshAllEndpoints: function () {
            if (!jsPlumbInstance) return;

            console.log("Refreshing all endpoints");

            // Get all nodes in the canvas
            const nodes = document.querySelectorAll('.workflow-canvas [data-node-id]');

            // First, remove all endpoints to avoid duplicates
            jsPlumbInstance.deleteEveryEndpoint();

            // Then recreate endpoints for each node
            nodes.forEach(node => {
                const nodeId = node.getAttribute('data-node-id');
                if (nodeId) {
                    this.createEndpoints(node, nodeId);
                }
            });

            // Finally, repaint everything
            setTimeout(() => {
                jsPlumbInstance.repaintEverything();
                this.updateServerConnections();
            }, 100);
        },

        loadConnections: function (connectionsJson) {
            if (!jsPlumbInstance) {
                console.log("jsPlumb not initialized, initializing now...");
                jsPlumbInstance = this.initialize();

                // Return early and retry after initialization
                setTimeout(() => {
                    console.log("Retrying loadConnections after initialization");
                    this.loadConnections(connectionsJson);
                }, 500);
                return;
            }

            // Clear existing connections
            jsPlumbInstance.deleteEveryConnection();

            // Handle different possible formats of the connections data
            let connections = connectionsJson;

            // If it's a string, try to parse it as JSON
            if (typeof connectionsJson === 'string') {
                try {
                    connections = JSON.parse(connectionsJson);
                } catch (e) {
                    console.error("Failed to parse connections JSON string:", e);
                    return;
                }
            }

            // If it's an object with a connections property, extract that
            if (connections && typeof connections === 'object' && !Array.isArray(connections)) {
                if (connections.connections && Array.isArray(connections.connections)) {
                    connections = connections.connections;
                }
            }

            // Now ensure connections is an array before using forEach
            if (Array.isArray(connections)) {
                if (connections.length === 0) {
                    console.log("Loading workflow with no connections");
                    return;
                }

                // First, ensure all endpoints are created
                const nodes = document.querySelectorAll('[data-node-id]');
                nodes.forEach(node => {
                    const nodeId = node.getAttribute('data-node-id');
                    // Check if endpoints already exist
                    const endpoints = jsPlumbInstance.getEndpoints(node);
                    if (!endpoints || endpoints.length < 2) {
                        this.createEndpoints(node, nodeId);
                    }
                });

                // Then create connections with a shorter delay
                setTimeout(() => {
                    console.log("Creating " + connections.length + " connections");

                    // Batch connection creation for better performance
                    jsPlumbInstance.batch(() => {
                        connections.forEach(conn => {
                            try {
                                // Get source and target node IDs
                                const sourceNodeId = conn.source;
                                const targetNodeId = conn.target;

                                // Find the actual DOM elements by data-node-id
                                const sourceElement = document.querySelector(`[data-node-id="${sourceNodeId}"]`);
                                const targetElement = document.querySelector(`[data-node-id="${targetNodeId}"]`);

                                if (!sourceElement || !targetElement) {
                                    console.warn(`Cannot find elements for connection: ${sourceNodeId} -> ${targetNodeId}`);
                                    return;
                                }

                                // Create the connection using the elements
                                jsPlumbInstance.connect({
                                    source: sourceElement,
                                    target: targetElement,
                                    anchors: ["Right", "Left"]
                                });
                            } catch (e) {
                                console.error("Error creating connection:", e);
                            }
                        });
                    });

                    // Force a repaint after all connections are created
                    jsPlumbInstance.repaintEverything();
                }, 300); // Reduced delay to 300ms
            } else {
                console.error("Invalid connections format:", connectionsJson);
            }
        },

        updateServerConnections: function () {
            if (!jsPlumbInstance) return;

            const connections = this.getAllConnections();
            this.updateConnectionsWithDebounce(connections);
        },

        updateConnectionsWithDebounce: function (connections) {
            // Clear any pending timeout
            if (connectionUpdateTimeout) {
                clearTimeout(connectionUpdateTimeout);
            }

            // Set a new timeout to update connections after a delay
            connectionUpdateTimeout = setTimeout(function () {
                if (window.updateNodeConnections) {
                    window.updateNodeConnections(JSON.stringify(connections));
                }
            }, 300); // 300ms debounce time
        },

        toggleRemovalMode: function () {
            removalMode = !removalMode;

            if (removalMode) {
                jsPlumbInstance.bind("click", function (conn) {
                    jsPlumbInstance.deleteConnection(conn);
                    this.updateServerConnections();
                }.bind(this));
            } else {
                jsPlumbInstance.unbind("click");
            }
        },

        setEditMode: function (isEditMode) {
            editMode = isEditMode;

            // Update draggable state for all elements
            if (jsPlumbInstance) {
                const elements = document.querySelectorAll('.workflow-canvas .workflow-button');
                elements.forEach(el => {
                    if (isEditMode) {
                        // Make elements draggable in edit mode
                        jsPlumbInstance.draggable(el, {
                            containment: 'parent',
                            grid: [5, 5]
                        });
                        el.style.cursor = 'move';
                    } else {
                        // Disable dragging in view mode
                        jsPlumbInstance.destroyDraggable(el);
                        el.style.cursor = 'default';
                    }
                });
            }
        }
    };
})();