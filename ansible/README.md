# 🚀 Usage Examples
ansible-galaxy install -r requirements.yml

# Basic validation (localhost):
ansible-playbook validate_endpoints.yml

# Validate specific environment:
ansible-playbook validate_endpoints.yml -i inventory.yml -l production

# Override variables:
ansible-playbook validate_endpoints.yml -e "api_host=https://my-api.com db_host=my-db.com"

# Run only specific checks:
ansible-playbook validate_endpoints.yml --tags "http,network"

# Dry run (check mode):
ansible-playbook validate_endpoints.yml --check

# Parallel execution across multiple hosts:
ansible-playbook validate_endpoints.yml -f 10

# Generate only report:
ansible-playbook validate_endpoints.yml --tags "report"

# With vault for secrets:
ansible-playbook validate_endpoints.yml --ask-vault-pass
