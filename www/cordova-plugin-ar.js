var exec = require('cordova/exec');
var pluginName = 'ARPlugin';

/**
 * @callback PluginListener
 * @param {string} pluginMessage - ArKit information string in the following formats:
 *                                 "Camera: positionX, positionY, positionZ, quatirionX, quatirionY, quatirionZ, quatirionW"
 *                                 "qrNode: positionX, positionY, positionZ, quatirionX, quatirionY, quatirionZ, quatirionW"
 */

/**
 * Callback listener for ArKit changes
 * @param {PluginListener} success - The callback that handles plugin message.
 */
exports.onCameraUpdate = function (success, error) {
    exec(success, error, pluginName, 'setCameraListener');
};

exports.onQrFounded = function (success, error) {
    exec(success, error, pluginName, 'setOnQrFounded');
};

/**
 * Start AR session and add AR View below WebView
 * @constructor
 * @param options
 * @param {boolean} options.qrRecognitionEnabled
 * @param {string[]} options.qrData - Array of string information that should recognize by ArKit
 */
exports.startArSession = function(options = {qrRecognitionEnabled: false}) {
    exec(undefined, undefined, pluginName, 'addARView', [options]);
};

exports.start = function(options = {allowMultiplePoints: false, unit: 'cm', unitTxt: 'cm', startNew: false, startSceneform: true}, success, error) {
    exec(success, error, pluginName, 'addARView', [options]);
};

/// Stop AR session and remove AR View the from veiw stack
exports.stopArSession = function() {
    exec(undefined, undefined, pluginName, 'removeARView');
};

exports.restartArSession = function() {
    exec(undefined, undefined, pluginName, 'restartArSession');
};

exports.checkARCore = function(success, error) {
    exec(success, error, pluginName, 'checkARCore');
};

//Change mode of current AR session
// exports.startARSessionWithoutQRRecognition = function() {
    // exec(undefined, undefined, pluginName, 'startARSessionWithoutQRRecognition');
// };

//Change mode of current AR session with QR support
// exports.startARSessionWithQRRecognition = function(qrDataArr) {
    // exec(undefined, undefined, pluginName, 'startARSessionWithQRRecognition', qrDataArr);
// };