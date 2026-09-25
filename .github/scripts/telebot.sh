#!/bin/env bash
# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.

msg="*$TITLE*
\\#ci\\_$VERSION
\`\`\`
$COMMIT_MESSAGE
\`\`\`
[Commit]($COMMIT_URL)
[Workflow run]($RUN_URL)
"

file="$1"
thumbnail="$GITHUB_WORKSPACE/logo.jpg"

if [ ! -f "$file" ]; then
    echo "error: File not found" >&2
    exit 1
fi

curl -s "https://api.telegram.org/bot$BOT_TOKEN/sendDocument" \
	-F document=@"$file" \
	-F chat_id="$CHAT_ID" \
	-F "disable_web_page_preview=true" \
	-F "parse_mode=markdownv2" \
	-F thumb=@"$thumbnail" \
	-F caption="$msg"
