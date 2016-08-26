var vertDisplay = new JustGage({
  id: "vertgauge",
  value: 0,
  min: -45,
  max: 45,
  titlePosition: 'below',
  noGradient: true,
  levelColors: ['#673AB7', '#009688', '#FFC107'],
  startAnimationTime: 100,
  refreshAnimationTime: 0,
  title: "Vertical Angle"
});

var horizDisplay = new JustGage({
  id: "horizgauge",
  value: 0,
  min: -45,
  max: 45,
  titlePosition: 'below',
  noGradient: true,
  levelColors: ['#673AB7', '#009688', '#FFC107'],
  startAnimationTime: 100,
  refreshAnimationTime: 0,
  title: "Horizontal Angle"
});

var received_msg;

var ws = new WebSocket('ws://localhost:5801');
ws.binaryType = "arraybuffer";

ws.onopen = function(){
    // Web Socket is connected, send data using send()
    ws.send("hi there");
    console.log("Message is sent...");
};

ws.onmessage = function (evt) {
    received_msg = evt.data;
    switch(typeof received_msg){
      case 'string':
        console.log("msg: " + received_msg);
        break;
      case 'object':
        var dv = new DataView(received_msg);
        var vert = dv.getFloat64(12) * (180/Math.PI);
        vertDisplay.refresh(vert);
        var horiz = dv.getFloat64(20) * (-180/Math.PI);
        horizDisplay.refresh(horiz);
        break;
    }
      console.log(received_msg);
};

ws.onclose = function() {
    // websocket is closed.
    console.log("Connection is closed...");
};
