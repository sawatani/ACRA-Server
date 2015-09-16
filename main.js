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

var formatISO8601Date = function(d) {
	if (!d) d = new Date();
	function pad(n) { return n < 10 ? '0' + n : n }
	log("Formatting date:", d);
	
	return d.getUTCFullYear() + '-'
    + pad(d.getUTCMonth() +1) + '-'
    + pad(d.getUTCDate()) + 'T'
    + pad(d.getUTCHours()) + ':'
    + pad(d.getUTCMinutes()) + ':'
    + pad(d.getUTCSeconds()) + '.'
    + d.getUTCMilliseconds() + 'Z'
}

var checkAuth = function(line, config, next) {
	var basics = line.split(" ");
	if (basics.length != 2 || basics[0].toUpperCase() != "BASIC") {
		next("Authorization failure : No basic authorization");
	} else {
		var decoded = new Buffer(basics[1], 'base64').toString();
		var array = decoded.split(":");
		if (array.length != 2) {
			next("Authorization failure: Illegal format for basic authorization");
		} else {
			if (array[0] != config.username || array[1] != config.password) {
				next("Authorization failure: Not match username or password");
			} else {
				next(null, config);
			}
		}
	}
} 

exports.handler = function(event, context) {
    log('Received event:', event);

    var region = event.region;
    var appName = event.appName;
    var reportId = event.reportId;
    var report = event.report;
    var auth = event.authorization;
    
    aws.config.update({region: region});
    var docClient = new dbdoc.DynamoDB();
    
    function tableName(name) { return "ACRA." + name }
    
    async.waterfall(
    		[
    		 function(next) {
    			 docClient.getItem({
    				 TableName: tableName("APPLICATIONS"),
    				 Key: {
    					 ID: appName
    				 }
    			 }, next);
    		 },
    		 function(res, next) {
    			 if (!res.Item) {
    				 next("Not found: " + appName)
    			 } else {
    				 log("Authorizing for", appName);
    				 checkAuth(auth, res.Item.CONFIG, next);
    			 }
    		 },
    		 function(config, next) {
    			 var params = {
        				 TableName: tableName(config.tableName),
        				 Item: {
        					 ID: reportId,
        					 CREATED_AT: formatISO8601Date()
        				 }
    			 }
    			 log("Puting report:", params);
    			 params.Item.REPORT = report;
    			 
    			 docClient.putItem(params, next);
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
