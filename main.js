var async = require('async');
var aws = require("aws-sdk");
var dbdoc = require('dynamodb-doc');

var log = function() {
	var args = Array.prototype.map.call(arguments, function(value) {
		if (typeof value === 'string') {
			return value;
		} else {
			return JSON.stringify(value, null, 2)
		}
	});
	console.log.apply(this, args);
}

exports.handler = function(event, context) {
    log('Received event:', event);

    var region = event.region;
    
    aws.config.update({region: region});
    var docClient = new dbdoc.DynamoDB();
    
    async.waterfall(
    		[
    		 function(next) {
    			 
    		 }
            ],
    		function(err, result) {
    			if (err) {
    				context.fail(err);
    			} else {
    				log("Result: ", result);
    				context.succeed(result);
    			}
    		});
}
