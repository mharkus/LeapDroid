var Leap = require('../lib').Leap
var net = require('net');


var mSocket;
var started = false;
var previousFrame;

var server = net.createServer(function(socket){
	mSocket = socket;
	started = true;
	
	console.log("client connected!");
	
	socket.on("disconnect", function(){
		started = false;
	})
	
});
Leap.loop({enableGestures: true}, function(frame, done) {
		if(started){
			if(frame.hands.length > 0){
				var hand = frame.hands[0];
				mSocket.write(hand.rotation + ":" + hand.palmPosition.toString() + "\n");
			}
		}
		done();
})

server.listen(1337, '192.168.0.25');