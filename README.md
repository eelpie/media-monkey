#Media Monkey

Multi media file wrangling microservice.

Wraps the Tiki, ImageMagik, avconv and mediainfo tools with an HTTP/JSON interface.


###POST /meta

Attempt to format detect and extract meta data from an uploaded file.
The media file should be sent as raw bytes on the request body.


###POST /scale

width, height, rotate
Accepts header

Scale the posted image and return it as a GIF, JPEG or PNG.

The image file should be sent as raw bytes on the request body.
The desired return image format should be specified using the Accepts header on the request.


###POST /scale/callback

widget, height, rotate, callback

Asynchronous version of the scale endpoint.

Scales the the posted image then HTTP POSTs the result back to the url specified in the callback parameter.


####POST /video/thumbnail


####POST /video/transcode


####POST /video/transcode/scale

Asynchronous version of the transcode endpoint.