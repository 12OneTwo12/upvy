output "rds_endpoint" {
  description = "RDS endpoint address"
  value       = aws_db_instance.main.endpoint
}

output "s3_ai_media_bucket" {
  description = "AI media S3 bucket name"
  value       = aws_s3_bucket.ai_media.id
}

output "s3_images_bucket" {
  description = "Images S3 bucket name"
  value       = aws_s3_bucket.images.id
}

output "ec2_public_ip" {
  description = "EC2 Elastic IP"
  value       = aws_eip.app.public_ip
}

output "ec2_instance_id" {
  description = "EC2 instance ID"
  value       = aws_instance.app.id
}

output "ssh_private_key" {
  description = "SSH private key for EC2 access (save to ~/.ssh/upvy-ec2)"
  value       = tls_private_key.deploy.private_key_openssh
  sensitive   = true
}
