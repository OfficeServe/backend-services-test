#! /bin/bash -pe

AWS_DEFAULT_REGION=us-east-1
: ${USER_POOL_ID:=${AWS_DEFAULT_REGION}_kRLWhrgSj}
: ${IDENTITY_POOL_ID:=$AWS_DEFAULT_REGION:e7550ab6-a426-45a5-a38d-7c42906f16a8}
: ${USERNAME:=nicolas.cavallo@gmail.com}
: ${PASSWORD:=Test123456}
# The client ID must have admin login enabled for it in the AWS console
: ${CLIENT_ID:=1idilu0cepcjto3ifk2e4u0mrj}

die() {
  echo $* >&2
  exit 1
}

require_command() {
  which $1 >/dev/null || die ${2:-$1} must be installed
}

check_aws_version() {
  declare -a version
  version=($(aws --version|sed -e 's|.*aws-cli/\([0-9]\+\.[0-9]\+\.[0-9]\+\).*|\1|'|tr . ' '))
  if [ ${version[0]} -le 1 ] && [ ${version[1]} -le 0 ] && [ ${version[2]} -lt 51 ]; then
      die aws CLI version is too old, you need to upgrade
  fi
}

get_issuer() {
  echo $1 | cut -d. -f2 | base64 --decode 2>/dev/null | jq -r .iss
}

# Removes the protocol part from an URL and outputs the rest
remove_protocol() {
  sed -e 's|.*://||'
}

require_command aws "AWS CLI"
check_aws_version
require_command jq
require_command base64 "GNU coreutils"

id_token=$(aws cognito-idp admin-initiate-auth --user-pool-id $USER_POOL_ID \
    --auth-flow ADMIN_NO_SRP_AUTH --auth-parameters USERNAME=$USERNAME,PASSWORD=$PASSWORD \
    --client-id $CLIENT_ID | jq .AuthenticationResult.IdToken)
identity_id=$(aws cognito-identity get-id --identity-pool-id $IDENTITY_POOL_ID \
    --logins "{\"$(get_issuer $id_token | remove_protocol)\": $id_token}" | jq -r .IdentityId)
aws cognito-identity get-credentials-for-identity --identity-id $identity_id \
    --logins "{\"$(get_issuer $id_token | remove_protocol)\": $id_token}"
