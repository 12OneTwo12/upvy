resource "aws_db_instance" "main" {
  identifier = "upvy-db"

  engine         = "mysql"
  engine_version = "8.0.44"

  instance_class    = "db.t4g.micro"
  allocated_storage = 20
  storage_type      = "gp2"
  storage_encrypted = true

  username = "root"
  password = var.rds_password

  multi_az            = false
  publicly_accessible = true
  deletion_protection = true

  db_subnet_group_name   = "default-vpc-013cdfc535f2acec5"
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = "default.mysql8.0"

  backup_retention_period = 2
  copy_tags_to_snapshot   = true
  skip_final_snapshot     = true
  max_allocated_storage   = 1000

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [password]
  }
}
