resource "aws_s3_bucket" "ai_media" {
  bucket = "upvy-ai-media-bucket"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "ai_media" {
  bucket = aws_s3_bucket.ai_media.id

  rule {
    bucket_key_enabled = true

    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_policy" "ai_media" {
  bucket = aws_s3_bucket.ai_media.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowGrowSnapServiceAccount"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::153758676215:user/grow-snap-s3-service-account" }
        Action    = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:PutObjectAcl"]
        Resource  = "arn:aws:s3:::upvy-ai-media-bucket/*"
      },
      {
        Sid       = "AllowListBucket"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::153758676215:user/grow-snap-s3-service-account" }
        Action    = "s3:ListBucket"
        Resource  = "arn:aws:s3:::upvy-ai-media-bucket"
      },
      {
        Sid       = "PublicReadForPublishedContent"
        Effect    = "Allow"
        Principal = "*"
        Action    = "s3:GetObject"
        Resource = [
          "arn:aws:s3:::upvy-ai-media-bucket/edited-videos/*",
          "arn:aws:s3:::upvy-ai-media-bucket/thumbnails/*"
        ]
      }
    ]
  })
}

resource "aws_s3_bucket" "images" {
  bucket = "upvy-images-bucket"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    bucket_key_enabled = true

    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_policy" "images" {
  bucket = aws_s3_bucket.images.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowGrowSnapServiceAccount"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::153758676215:user/grow-snap-s3-service-account" }
        Action    = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:PutObjectAcl"]
        Resource  = "arn:aws:s3:::upvy-images-bucket/*"
      },
      {
        Sid       = "AllowListBucket"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::153758676215:user/grow-snap-s3-service-account" }
        Action    = "s3:ListBucket"
        Resource  = "arn:aws:s3:::upvy-images-bucket"
      },
      {
        Sid       = "PublicReadGetObject"
        Effect    = "Allow"
        Principal = "*"
        Action    = "s3:GetObject"
        Resource = [
          "arn:aws:s3:::upvy-images-bucket/profile-images/*",
          "arn:aws:s3:::upvy-images-bucket/contents/*"
        ]
      }
    ]
  })
}
