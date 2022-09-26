# bash helper functions
nxf_cp_retry() {
    local max_attempts=20
    local timeout=30
    local attempt=0
    local exitCode=0
    while (( $attempt < $max_attempts ))
    do
      if "$@"
        then
          return 0
      else
        exitCode=$?
      fi
      if [[ $exitCode == 0 ]]
      then
        break
      fi
      nxf_sleep $timeout
      attempt=$(( attempt + 1 ))
      timeout=$(( timeout * 2 ))
    done
}

nxf_parallel() {
    IFS=$'\n'
    local cmd=("$@")
    local cpus=$(nproc 2>/dev/null || < /proc/cpuinfo grep '^process' -c)
    local max=$(if (( cpus>10 )); then echo 10; else echo $cpus; fi)
    local i=0
    local pid=()
    (
    set +u
    while ((i<${#cmd[@]})); do
        local copy=()
        for x in "${pid[@]}"; do
          [[ -e /proc/$x ]] && copy+=($x)
        done
        pid=("${copy[@]}")

        if ((${#pid[@]}>=$max)); then
          nxf_sleep 0.2
        else
          eval "${cmd[$i]}" &
          pid+=($!)
          ((i+=1))
        fi
    done
    for p in "${pid[@]}"; do
        wait $p
    done
    )
    unset IFS
}

# Env variables used for azcopy opts
export _AZCOPY_BLOCK_SIZE_MB=10
export _AZCOPY_BLOCK_BLOB_TIER=Hot
export _AZCOPY_PUT_MD5="--put-md5"
export _AZCOPY_CHECK_MD5="FailIfDifferent"
export _AZCOPY_OVERWRITE=false

nxf_az_upload() {
    local name=$1
    local target=${2%/} ## remove ending slash
    local base_name="$(basename "$name")"
    local dir_name="$(dirname "$name")"

    if [[ -d $name ]]; then
      if [[ "$base_name" == "$name" ]]; then
        azcopy cp "$name" "$target?$AZ_SAS" --recursive --block-blob-tier $_AZCOPY_BLOCK_BLOB_TIER --block-size-mb $_AZCOPY_BLOCK_SIZE_MB --overwrite $_AZCOPY_OVERWRITE $_AZCOPY_PUT_MD5
      else
        azcopy cp "$name" "$target/$dir_name?$AZ_SAS" --recursive --block-blob-tier $_AZCOPY_BLOCK_BLOB_TIER --block-size-mb $_AZCOPY_BLOCK_SIZE_MB --overwrite $_AZCOPY_OVERWRITE $_AZCOPY_PUT_MD5
      fi
    else
      azcopy cp "$name" "$target/$name?$AZ_SAS" --block-blob-tier $_AZCOPY_BLOCK_BLOB_TIER --block-size-mb $_AZCOPY_BLOCK_SIZE_MB --overwrite $_AZCOPY_OVERWRITE $_AZCOPY_PUT_MD5
    fi
}

nxf_az_download() {
    local source=$1
    local target=$2
    local basedir=$(dirname $2)
    local ret
    mkdir -p "$basedir"

    ret=$(azcopy cp "$source?$AZ_SAS" "$target" 2>&1) || {
        ## if fails check if it was trying to download a directory
        mkdir -p $target
        azcopy cp "$source/*?$AZ_SAS" "$target" --recursive --check-md5 $_AZCOPY_CHECK_MD5 --overwrite $_AZCOPY_OVERWRITE >/dev/null || {
            rm -rf $target
            >&2 echo "Unable to download path: $source"
            exit 1
        }
    }
}
