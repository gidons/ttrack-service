#!/bin/bash

# Usage: create_clerk_session_token "secret_key" "user_id" seconds
create_clerk_session_token() {
    local secret_key=$(az keyvault secret show --vault-name TTrackSecrets -n ClerkAPIKeyTest --query "value" -o tsv)
    local user_id="${1-user_37cpPAUMRsjkwXKQzFOrXoSFttX}"
    local seconds="${2-3600}"

    if [ -z "$secret_key" ] || [ -z "$user_id" ] || [ -z "$seconds" ]; then
        echo "Error: Missing required arguments"
        echo "Usage: create_clerk_session_token <secret_key> <user_id> <seconds>"
        return 1
    fi

    if ! [[ "$seconds" =~ ^[0-9]+$ ]]; then
        echo "Error: seconds must be a positive integer"
        return 1
    fi

    #echo "Creating session for user: $user_id"

    local session_response
    session_response=$(curl -s https://api.clerk.com/v1/sessions \
        --request POST \
        --header 'Content-Type: application/json' \
        --header "Authorization: Bearer $secret_key" \
        --data "{\"user_id\": \"$user_id\"}")

    if [ $? -ne 0 ]; then
        echo "Error: Failed to create session"
        return 1
    fi

    local session_id
    if command -v jq &> /dev/null; then
        session_id=$(echo "$session_response" | jq -r '.id')
        if [ "$session_id" = "null" ] || [ -z "$session_id" ]; then
            echo "Error: Could not extract session ID from response"
            echo "Response: $session_response"
            return 1
        fi
    else
        session_id=$(echo "$session_response" | grep -o '"id":"[^"]*"' | head -1 | sed 's/"id":"\([^"]*\)"/\1/')
        if [ -z "$session_id" ]; then
            echo "Error: Could not extract session ID from response (jq not available)"
            echo "Response: $session_response"
            echo "Consider installing jq for better JSON parsing"
            return 1
        fi
    fi

#    echo "Session created with ID: $session_id" 1>2

#    echo "Creating token with expiration: $seconds seconds" 1>2

    local token_response
    token_response=$(curl -s "https://api.clerk.com/v1/sessions/$session_id/tokens" \
        --request POST \
        --header 'Content-Type: application/json' \
        --header "Authorization: Bearer $secret_key" \
        --data "{\"expires_in_seconds\": $seconds}")

    if [ $? -ne 0 ]; then
        echo "Error: Failed to create token"
        return 1
    fi

#    echo "Token created successfully!"

    if command -v jq &> /dev/null; then
        local token
        token=$(echo "$token_response" | jq -r '.jwt')
        if [ "$token" != "null" ] && [ -n "$token" ]; then
#            echo "Extracted token: $token"
            echo $token
        fi
    fi

    return 0
}

create_clerk_session_token