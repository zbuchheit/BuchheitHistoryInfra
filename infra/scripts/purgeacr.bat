:: Delete repo images older than 5d
az acr run --cmd "acr purge --filter 'buchheithistoryapi:.*' --ago 5d --untagged --dry-run" --registry buchheithistoryacr /dev/null