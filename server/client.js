var websock;
var sessionLog;

function resetConnectForm(form) {
    form.elements.endpointUrl.disabled = false;
    form.elements.submit.disabled = false;
    form.elements.disconnect.disabled = true;
}

function connectConnectForm(form) {
    form.elements.endpointUrl.disabled = true;
    form.elements.submit.disabled = true;
}

function connectedConnectForm(form) {
    form.elements.disconnect.disabled = false;
}

function resetInputForm(form) {
    form.elements.input.disabled = true;
    form.elements.submit[0].disabled = true;
    form.elements.submit[1].disabled = true;
}

function connectedInputForm(form) {
    form.elements.input.disabled = false;
    form.elements.submit[0].disabled = false;
    form.elements.submit[1].disabled = false;
}

function appendLog(msg) {
    sessionLog.value = sessionLog.value + msg;
    sessionLog.scrollTop = sessionLog.scrollHeight;
}

function handleOpenSocket(e) {
    connectedConnectForm(document.connectForm);
    connectedInputForm(document.inputForm);
    document.inputForm.elements.input.focus();
}

function handleSocketIncoming(e) {
    e.data.text().then(function (text) {
        appendLog(text);
    });
}

function handleCloseSocket(e) {
    if (e.code == 1000)
        appendLog("Connection closed\n");
    else
        appendLog("Connection closed abnormally: " + e.code + (e.reason != "" ? " " + e.reason : "") + "\n");
    resetConnectForm(document.connectForm);
    resetInputForm(document.inputForm);
}

function handleSocketError(e) {
    appendLog("An error occurred\n");
    websock.close();
}

function handleConnectFormSubmit(e) {
    e.preventDefault();
    const form = e.currentTarget;
    const url = form.elements.endpointUrl.value;
    connectConnectForm(form);
    sessionLog.disabled = false;
    sessionLog.value = "";
    appendLog("Connecting to " + url + "\n");
    websock = new WebSocket(url);
    websock.onopen = handleOpenSocket;
    websock.onmessage = handleSocketIncoming;
    websock.onclose = handleCloseSocket;
    websock.onerror = handleSocketError;
}

function handleInputFormSubmit(e) {
    e.preventDefault();
    const form = e.currentTarget;
    if (e.submitter.value == "EOF") {
        websock.send(new Blob());
    } else {
        const msg = form.elements.input.value + "\n";
        appendLog(msg);
        websock.send(new Blob([msg]));
        form.elements.input.value = "";
    }
}

document.addEventListener("DOMContentLoaded", function () {
    document.connectForm.onsubmit = handleConnectFormSubmit;
    document.connectForm.elements.disconnect.onclick = function (e) { websock.close(); };
    document.inputForm.onsubmit = handleInputFormSubmit;
    sessionLog = document.getElementById("sessionLog");
    resetConnectForm(document.connectForm);
    resetInputForm(document.inputForm);
    sessionLog.value = "(empty)";
    sessionLog.disabled = true;
    const url = new URL(window.location.href);
    document.connectForm.elements.endpointUrl.value =
        (url.protocol == "http:" ? "ws:" :
        url.protocol == "https:" ? "wss:" : url.protocol)
        + "//" + url.host + url.pathname;
    document.connectForm.elements.endpointUrl.focus();
});
