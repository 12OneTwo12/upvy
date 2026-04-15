terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  backend "s3" {
    bucket         = "upvy-terraform-state"
    key            = "terraform.tfstate"
    region         = "ap-northeast-2"
    encrypt        = true
    dynamodb_table = "upvy-terraform-lock"
    profile        = "upvy"
  }
}
