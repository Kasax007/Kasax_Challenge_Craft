# fin.sh NAME: copy srt + voice track to out/send
SP=/tmp/claude-0/-home-user-Kasax-Challenge-Craft/2061deb3-7247-5b5c-aa62-6c43681c8da7/scratchpad
cp $SP/out/$1.srt $SP/out/send/$1.srt
ffmpeg -v error -y -i $SP/out/$1_voice.wav -c:a aac -b:a 160k $SP/out/send/$1_voice.m4a
ls -la $SP/out/send | grep $1
