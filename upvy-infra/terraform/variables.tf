variable "rds_password" {
  description = "RDS master password"
  type        = string
  sensitive   = true
}

variable "allowed_ssh_cidrs" {
  description = "CIDR blocks allowed to SSH (includes 0.0.0.0/0 for GitHub Actions CI/CD deploy)"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}
