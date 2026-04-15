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
