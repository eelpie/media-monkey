#Media Monkey

Multi media file wrangling.

###POST /meta

Attempt to format detect and extract meta data from an uploaded file.
The media file should be sent as raw bytes on the request body.


###POST /scale

width, height, rotate

Scale the posted image and return it as a JPEG.
The image file should be sent as raw bytes on the request body.


###POST /scale/callback

[(widget, height, rotate)] callback

Scale the posted image into a set of scaled JPEG files.
Call the calling application on the supplied callback url upon completion.
Retain the processed media files locally for a specified amount of time to
allow the calling application to act on the callback.


