package com.lumera.app.remote_input

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lumera.app.data.model.HubRowItemEntity
import com.lumera.app.domain.HubShape
import fi.iki.elonen.NanoHTTPD
import java.util.UUID

class HubBulkUploadServer(
    port: Int,
    private val items: List<HubRowItemEntity>,
    private val shape: HubShape,
    private val onImageReceived: (String, ByteArray) -> Unit,
    private val onImageDeleted: ((String) -> Unit)? = null
) : NanoHTTPD(port) {

    // Track images uploaded during this session for preview and status updates
    private val uploadedPreviews = mutableMapOf<String, String>()
    // Track images deleted during this session
    private val deletedIds = mutableSetOf<String>()
    private val csrfToken = UUID.randomUUID().toString()

    companion object {
        private const val MAX_IMAGE_SIZE = 5 * 1024 * 1024 // 5 MB
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return when {
            uri == "/" -> servePage()
            uri == "/upload" && session.method == Method.POST -> handleImageUpload(session)
            uri == "/preview" && session.method == Method.GET -> servePreview(session)
            uri == "/delete" && session.method == Method.POST -> handleImageDelete(session)
            uri == "/ping" -> DisconnectBanner.pingResponse()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    private fun servePage(): Response {
        val previewWidth: Int
        val previewHeight: Int
        val cropWidth: Int
        val cropHeight: Int
        val shapeName: String

        when (shape) {
            HubShape.HORIZONTAL -> {
                previewWidth = 72; previewHeight = 40
                cropWidth = 320; cropHeight = 180
                shapeName = "Horizontal (16:9)"
            }
            HubShape.VERTICAL -> {
                previewWidth = 36; previewHeight = 54
                cropWidth = 200; cropHeight = 300
                shapeName = "Vertical (2:3)"
            }
            HubShape.SQUARE -> {
                previewWidth = 48; previewHeight = 48
                cropWidth = 250; cropHeight = 250
                shapeName = "Square (1:1)"
            }
        }

        val itemsJson = JsonArray().apply {
            items.forEach { item ->
                val wasDeleted = item.configUniqueId in deletedIds
                val hasImage = !wasDeleted && (item.customImageUrl != null || uploadedPreviews.containsKey(item.configUniqueId))
                val hasPreview = uploadedPreviews.containsKey(item.configUniqueId)
                add(JsonObject().apply {
                    addProperty("id", item.configUniqueId)
                    addProperty("title", item.title)
                    addProperty("hasImage", hasImage)
                    addProperty("hasPreview", hasPreview)
                })
            }
        }.toString()

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>Manage Hub Images</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background-color: #121212;
                        color: #ffffff;
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .container {
                        background-color: #1e1e1e;
                        border-radius: 16px;
                        padding: 24px;
                        width: 100%;
                        max-width: 400px;
                        box-shadow: 0 4px 6px rgba(0,0,0,0.3);
                        text-align: center;
                    }
                    h1 { font-size: 1.5rem; font-weight: 600; margin-bottom: 0.5rem; }
                    .subtitle { color: #aaaaaa; font-size: 14px; margin-bottom: 20px; }
                    .shape-badge {
                        display: inline-block;
                        background: #333;
                        color: #ccc;
                        padding: 4px 12px;
                        border-radius: 100px;
                        font-size: 12px;
                        margin-bottom: 12px;
                    }
                    .list-container {
                        display: flex;
                        flex-direction: column;
                        gap: 10px;
                        text-align: left;
                    }
                    .item-card {
                        background: #2a2a2a;
                        border: 1px solid #333;
                        border-radius: 12px;
                        padding: 12px 16px;
                        display: flex;
                        align-items: center;
                        cursor: pointer;
                        transition: background 0.2s;
                        gap: 12px;
                    }
                    .item-card:active { background: #353535; }
                    .item-info { flex: 1; min-width: 0; }
                    .item-title { font-weight: 500; font-size: 15px; margin-bottom: 3px; }
                    .item-status { font-size: 12px; color: #666; }
                    .item-status.uploaded { color: #4CAF50; }
                    .item-preview {
                        width: ${previewWidth}px;
                        height: ${previewHeight}px;
                        border-radius: 6px;
                        object-fit: cover;
                        flex-shrink: 0;
                    }
                    .arrow { color: #555; font-size: 20px; flex-shrink: 0; }
                    .upload-area {
                        border: 2px dashed #333;
                        border-radius: 12px;
                        padding: 3rem 1rem;
                        margin-bottom: 1rem;
                        cursor: pointer;
                        transition: all 0.2s;
                    }
                    .upload-area:active { background-color: #2a2a2a; border-color: #555; }
                    #fileInput { display: none; }
                    .canvas-wrapper {
                        position: relative;
                        overflow: hidden;
                        border: 2px solid #444;
                        border-radius: 8px;
                        margin: 0 auto 1rem;
                        width: ${cropWidth}px;
                        height: ${cropHeight}px;
                    }
                    canvas { cursor: move; display: block; }
                    .slider-container { width: 100%; margin: 10px 0; }
                    .slider-container label { color: #aaaaaa; font-size: 12px; }
                    input[type=range] {
                        width: 100%;
                        -webkit-appearance: none;
                        background: #333;
                        height: 6px;
                        border-radius: 3px;
                        margin-top: 6px;
                    }
                    input[type=range]::-webkit-slider-thumb {
                        -webkit-appearance: none;
                        width: 24px;
                        height: 14px;
                        background: #ffffff;
                        border-radius: 7px;
                        cursor: pointer;
                    }
                    .btn {
                        width: 100%;
                        padding: 14px 24px;
                        font-size: 1rem;
                        font-weight: 600;
                        border: none;
                        border-radius: 24px;
                        background-color: #ffffff;
                        color: #000000;
                        cursor: pointer;
                        transition: transform 0.1s, opacity 0.2s;
                        margin-top: 8px;
                    }
                    .btn:active { transform: scale(0.98); }
                    .btn:disabled { opacity: 0.6; cursor: not-allowed; }
                    .btn-secondary { background: #333; color: #fff; }
                    .btn-danger {
                        background: transparent;
                        color: #ff5252;
                        border: none;
                        padding: 12px;
                        font-size: 0.85rem;
                        cursor: pointer;
                        margin-top: 12px;
                        width: 100%;
                    }
                    .view { display: none; }
                    .view.active { display: block; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div id="list-view" class="view active">
                        <span class="shape-badge">${shape.name}</span>
                        <h1>Manage Hub Images</h1>
                        <p class="subtitle">Select an item to upload a custom image.</p>
                        <div class="list-container" id="itemList"></div>
                    </div>

                    <div id="upload-view" class="view">
                        <span class="shape-badge">$shapeName</span>
                        <h1 id="uploadTitle"></h1>
                        <p class="subtitle">Upload a custom image</p>
                        <div class="upload-area" id="uploadArea">
                            <div style="font-size:2rem;margin-bottom:1rem;">🖼️</div>
                            <div>Tap to choose image</div>
                        </div>
                        <input type="file" id="fileInput" accept="image/*">
                        <button class="btn btn-secondary" id="backBtn">Back</button>
                        <button class="btn-danger" id="removeBtn" style="display:none">Remove current image</button>
                    </div>

                    <div id="crop-view" class="view">
                        <div class="canvas-wrapper">
                            <canvas id="cropCanvas" width="$cropWidth" height="$cropHeight"></canvas>
                        </div>
                        <div class="slider-container">
                            <label>Zoom</label>
                            <input type="range" id="zoomSlider">
                        </div>
                        <button class="btn" id="uploadBtn">Set Image</button>
                        <button class="btn btn-secondary" id="cancelCropBtn">Cancel</button>
                    </div>
                </div>

                <script>
                    var CROP_WIDTH = $cropWidth;
                    var CROP_HEIGHT = $cropHeight;
                    var items = $itemsJson;
                    var previews = {};

                    var listView = document.getElementById('list-view');
                    var uploadView = document.getElementById('upload-view');
                    var cropView = document.getElementById('crop-view');
                    var itemList = document.getElementById('itemList');
                    var uploadTitle = document.getElementById('uploadTitle');
                    var uploadArea = document.getElementById('uploadArea');
                    var fileInput = document.getElementById('fileInput');
                    var backBtn = document.getElementById('backBtn');
                    var removeBtn = document.getElementById('removeBtn');
                    var canvas = document.getElementById('cropCanvas');
                    var ctx = canvas.getContext('2d');
                    var zoomSlider = document.getElementById('zoomSlider');
                    var uploadBtn = document.getElementById('uploadBtn');
                    var cancelCropBtn = document.getElementById('cancelCropBtn');

                    var currentItemId = null;
                    var img = new Image();
                    var imgX = 0, imgY = 0, imgScale = 1;
                    var minScale = 0.1, maxScale = 3;
                    var isDragging = false;
                    var startX, startY;
                    var initialPinchDistance = null;
                    var initialScale = 1;
                    var startPosX = 0, startPosY = 0;

                    function showView(v) {
                        listView.classList.remove('active');
                        uploadView.classList.remove('active');
                        cropView.classList.remove('active');
                        v.classList.add('active');
                    }

                    function esc(s) {
                        return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
                    }

                    function renderList() {
                        itemList.innerHTML = items.map(function(item) {
                            var sc = item.hasImage ? 'item-status uploaded' : 'item-status';
                            var st = item.hasImage ? 'Image uploaded' : 'No Image';
                            var pv = previews[item.id]
                                ? '<img class="item-preview" src="' + previews[item.id] + '">'
                                : (item.hasPreview ? '<img class="item-preview" src="/preview?id=' + encodeURIComponent(item.id) + '">' : '');
                            return '<div class="item-card" data-id="' + item.id + '">'
                                + pv
                                + '<div class="item-info"><div class="item-title">' + esc(item.title) + '</div>'
                                + '<div class="' + sc + '">' + st + '</div></div>'
                                + '<div class="arrow">\u203a</div></div>';
                        }).join('');
                        itemList.querySelectorAll('.item-card').forEach(function(card) {
                            card.addEventListener('click', function() { openUpload(card.dataset.id); });
                        });
                    }

                    function openUpload(id) {
                        currentItemId = id;
                        var item = items.find(function(i) { return i.id === id; });
                        uploadTitle.textContent = item.title;
                        removeBtn.style.display = item.hasImage ? 'block' : 'none';
                        fileInput.value = '';
                        showView(uploadView);
                    }

                    uploadArea.onclick = function() { fileInput.click(); };

                    fileInput.onchange = function(e) {
                        var file = e.target.files[0];
                        if (file) {
                            var reader = new FileReader();
                            reader.onload = function(ev) {
                                img.onload = function() {
                                    resetCrop();
                                    draw();
                                    showView(cropView);
                                };
                                img.src = ev.target.result;
                            };
                            reader.readAsDataURL(file);
                        }
                    };

                    backBtn.onclick = function() { currentItemId = null; showView(listView); };
                    cancelCropBtn.onclick = function() { showView(uploadView); };

                    removeBtn.onclick = function() {
                        if (!confirm('Remove the image for this item?')) return;
                        var delForm = new FormData(); delForm.append('csrf_token', '${csrfToken}');
                        fetch('/delete?id=' + encodeURIComponent(currentItemId), { method: 'POST', body: delForm }).then(function(res) {
                            if (res.ok) {
                                var item = items.find(function(i) { return i.id === currentItemId; });
                                item.hasImage = false;
                                item.hasPreview = false;
                                delete previews[currentItemId];
                                currentItemId = null;
                                renderList();
                                showView(listView);
                            } else { alert('Failed to remove image'); }
                        }).catch(function(err) { alert('Error: ' + err); });
                    };

                    function resetCrop() {
                        var scaleW = CROP_WIDTH / img.width;
                        var scaleH = CROP_HEIGHT / img.height;
                        var coverScale = Math.max(scaleW, scaleH);
                        minScale = coverScale * 0.5;
                        maxScale = coverScale * 3;
                        imgScale = coverScale;
                        imgX = (CROP_WIDTH - img.width * imgScale) / 2;
                        imgY = (CROP_HEIGHT - img.height * imgScale) / 2;
                        zoomSlider.min = minScale;
                        zoomSlider.max = maxScale;
                        zoomSlider.step = (maxScale - minScale) / 100;
                        zoomSlider.value = imgScale;
                    }

                    function draw() {
                        ctx.clearRect(0, 0, CROP_WIDTH, CROP_HEIGHT);
                        ctx.drawImage(img, imgX, imgY, img.width * imgScale, img.height * imgScale);
                    }

                    function getPinchDistance(touches) {
                        var dx = touches[0].clientX - touches[1].clientX;
                        var dy = touches[0].clientY - touches[1].clientY;
                        return Math.sqrt(dx * dx + dy * dy);
                    }

                    canvas.onmousedown = function(e) { isDragging = true; startX = e.offsetX - imgX; startY = e.offsetY - imgY; };
                    canvas.onmousemove = function(e) { if (isDragging) { imgX = e.offsetX - startX; imgY = e.offsetY - startY; draw(); } };
                    canvas.onmouseup = function() { isDragging = false; };
                    canvas.onmouseleave = function() { isDragging = false; };

                    canvas.addEventListener('touchstart', function(e) {
                        e.preventDefault();
                        if (e.touches.length === 1) {
                            isDragging = true;
                            var rect = canvas.getBoundingClientRect();
                            startX = e.touches[0].clientX - rect.left - imgX;
                            startY = e.touches[0].clientY - rect.top - imgY;
                        } else if (e.touches.length === 2) {
                            isDragging = false;
                            initialPinchDistance = getPinchDistance(e.touches);
                            initialScale = imgScale;
                            startPosX = imgX;
                            startPosY = imgY;
                        }
                    });
                    canvas.addEventListener('touchmove', function(e) {
                        e.preventDefault();
                        if (e.touches.length === 1 && isDragging) {
                            var rect = canvas.getBoundingClientRect();
                            imgX = e.touches[0].clientX - rect.left - startX;
                            imgY = e.touches[0].clientY - rect.top - startY;
                            draw();
                        } else if (e.touches.length === 2 && initialPinchDistance !== null) {
                            var currentDistance = getPinchDistance(e.touches);
                            var zoomChange = currentDistance / initialPinchDistance;
                            var newScale = Math.min(Math.max(initialScale * zoomChange, minScale), maxScale);
                            var cx = CROP_WIDTH / 2;
                            var cy = CROP_HEIGHT / 2;
                            imgX = cx - (cx - startPosX) * (newScale / initialScale);
                            imgY = cy - (cy - startPosY) * (newScale / initialScale);
                            imgScale = newScale;
                            zoomSlider.value = imgScale;
                            draw();
                        }
                    });
                    canvas.addEventListener('touchend', function(e) {
                        isDragging = false;
                        if (e.touches.length < 2) initialPinchDistance = null;
                    });

                    zoomSlider.oninput = function() {
                        var oldScale = imgScale;
                        var newScale = parseFloat(zoomSlider.value);
                        var cx = CROP_WIDTH / 2;
                        var cy = CROP_HEIGHT / 2;
                        imgX = cx - (cx - imgX) * (newScale / oldScale);
                        imgY = cy - (cy - imgY) * (newScale / oldScale);
                        imgScale = newScale;
                        draw();
                    };

                    uploadBtn.onclick = function() {
                        uploadBtn.disabled = true;
                        uploadBtn.textContent = 'Uploading...';
                        var dataUrl = canvas.toDataURL('image/jpeg', 0.85);
                        var base64 = dataUrl.split(',')[1];
                        var formData = new FormData();
                        formData.append('image', base64);
                        formData.append('csrf_token', '${csrfToken}');
                        fetch('/upload?id=' + encodeURIComponent(currentItemId), { method: 'POST', body: formData })
                            .then(function(res) {
                                if (res.ok) {
                                    var item = items.find(function(i) { return i.id === currentItemId; });
                                    item.hasImage = true;
                                    item.hasPreview = true;
                                    previews[currentItemId] = dataUrl;
                                    currentItemId = null;
                                    renderList();
                                    showView(listView);
                                } else { alert('Upload failed'); }
                                uploadBtn.disabled = false;
                                uploadBtn.textContent = 'Set Image';
                            })
                            .catch(function(err) {
                                alert('Error: ' + err);
                                uploadBtn.disabled = false;
                                uploadBtn.textContent = 'Set Image';
                            });
                    };

                    renderList();
                </script>
                ${DisconnectBanner.htmlSnippet}
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun handleImageUpload(session: IHTTPSession): Response {
        try {
            val id = session.parms["id"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No ID")

            val files = mutableMapOf<String, String>()
            session.parseBody(files)

            val token = session.parms["csrf_token"]
            if (token != csrfToken) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Invalid request")
            }

            val base64Image = session.parms["image"]
            if (!base64Image.isNullOrBlank()) {
                val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)

                if (imageBytes.size > MAX_IMAGE_SIZE) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Image too large (max 5 MB)")
                }
                if (!isValidImage(imageBytes)) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid image format")
                }

                uploadedPreviews[id] = base64Image
                deletedIds.remove(id)
                onImageReceived(id, imageBytes)
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
            }

            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No image data")
        } catch (e: Exception) {
            if (com.lumera.app.BuildConfig.DEBUG) android.util.Log.w("HubBulkUploadServer", "Error processing upload", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error processing request")
        }
    }

    private fun isValidImage(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        // PNG
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        // JPEG
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        // WebP
        if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte()) return true
        return false
    }

    private fun handleImageDelete(session: IHTTPSession): Response {
        try {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)

            val token = session.parms["csrf_token"]
            if (token != csrfToken) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Invalid request")
            }

            val id = session.parms["id"]
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No ID")
            uploadedPreviews.remove(id)
            deletedIds.add(id)
            onImageDeleted?.invoke(id)
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } catch (e: Exception) {
            if (com.lumera.app.BuildConfig.DEBUG) android.util.Log.w("HubBulkUploadServer", "Error processing delete", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error")
        }
    }

    private fun servePreview(session: IHTTPSession): Response {
        val id = session.parms["id"]
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No ID")
        val base64 = uploadedPreviews[id]
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No preview")
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val stream = java.io.ByteArrayInputStream(bytes)
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", stream, bytes.size.toLong())
    }
}
