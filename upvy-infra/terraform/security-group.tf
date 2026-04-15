resource "aws_security_group" "rds" {
  name        = "UpvyDBSecurityGroup"
  description = "Created by RDS management console"
  vpc_id      = "vpc-013cdfc535f2acec5"

  ingress {
    description = "myip"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = ["49.175.194.2/32"]
  }

  ingress {
    description = "bootalk-egress-ip-2"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = ["34.64.229.219/32"]
  }

  ingress {
    description = "jeongils-home"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = ["182.220.181.34/32"]
  }

  ingress {
    description = "bootalk-egress-ip"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = ["34.22.74.100/32"]
  }

  ingress {
    description     = "ec2-app"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_security_group" "app" {
  name        = "upvy-app-sg"
  description = "Security group for upvy app server"
  vpc_id      = "vpc-013cdfc535f2acec5"

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.allowed_ssh_cidrs
  }

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
