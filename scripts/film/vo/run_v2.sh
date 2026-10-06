cd /tmp/claude-0/-home-user-Kasax-Challenge-Craft/2061deb3-7247-5b5c-aa62-6c43681c8da7/scratchpad
. tts/bin/activate
export HF_HOME=/tmp/claude-0/-home-user-Kasax-Challenge-Craft/2061deb3-7247-5b5c-aa62-6c43681c8da7/scratchpad/hf
nice -n 15 python vo/make.py $1 2>/dev/null > vo/$1.log
echo VO DONE >> vo/$1.log
