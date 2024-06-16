function createScoket(token) {
    let socket;
    if (typeof (WebSocket) == "undefined") {
        console.log("您的浏览器不支持 WebSocket");
    } else {
        //实现化WebSocket对象，指定要连接的服务器地址与端口  建立连接
        socket = new WebSocket("ws://localhost:8888/ws/" + token);
        //打开事件
        socket.onopen = function () {
            console.log("连接建立成功");
            //socket.send("这是来自客户端的消息" + location.href + new Date());
        };
        //获得消息事件
        socket.onmessage = function (result) {
            let msg = JSON.parse(result.data);
            console.log(msg.uuid + "：" + msg.data)
        };
        //关闭事件
        socket.onclose = function () {
            console.log("连接已关闭");
        };
        //发生了错误事件
        socket.onerror = function () {
            console.log("Socket发生了错误");
        }
        //窗口关闭
        window.onunload = function () {
            socket.close();
        }
    }
    return socket;
}
