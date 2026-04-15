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
