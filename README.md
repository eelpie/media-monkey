#Media Monkey

Multi media file wrangling microservice.
Wraps various open source media tools with an HTTP/JSON interface.

###POST /meta

Format detect and extract meta data from an uploaded binary.
Backed by Apache Tika and mediainfo.

The media file should be sent as raw bytes on the request body.

###POST /detect-faces

Attempt to detect faces in the posted image.
This information might be used to make an informed decision about how to crop an image.
Backed by openimaj.

###POST /crop

width, height, x, y

Crop an image

Scale the posted image and return it as a GIF, JPEG or PNG.
The desired return image format should be specified using the Accepts header on the request.
Backed by imagemagick.

###POST /scale

width, height, rotate, fill
callback
Accepts header

Scale the posted image and return it as a GIF, JPEG or PNG.
If a callback url as provided the service will return HTTP 202 Accepted and POST the processed media file back to your callback address at a later date.

The image file should be sent as raw bytes on the request body.
The desired return image format should be specified using the Accepts header on the request.
Backed by imagemagick.

####POST /video/transcode

Transcode a video.

