variable "rds_password" {
  description = "RDS master password"
  type        = string
  sensitive   = true
}

variable "allowed_ssh_cidrs" {
  description = "CIDR blocks allowed to SSH"
  type        = list(string)
  default     = ["49.175.194.2/32", "182.220.181.34/32"]
}
