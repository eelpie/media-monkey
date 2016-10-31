#Media Monkey

Multi media file wrangling microservice.

Wraps the Tiki, ImageMagik, avconv and mediainfo tools with an HTTP/JSON interface.


###POST /meta

Attempt to format detect and extract meta data from an uploaded file.
The media file should be sent as raw bytes on the request body.

###POST /crop

width, height, x, y

Crop an image

Scale the posted image and return it as a GIF, JPEG or PNG.
The desired return image format should be specified using the Accepts header on the request.


###POST /scale

width, height, rotate, fill
callback
Accepts header

Scale the posted image and return it as a GIF, JPEG or PNG.
If a callback url as provided the service will return HTTP 202 Accepted and POST the processed media file back to your callback address at a later date.

The image file should be sent as raw bytes on the request body.
The desired return image format should be specified using the Accepts header on the request.

####POST /video/transcode
