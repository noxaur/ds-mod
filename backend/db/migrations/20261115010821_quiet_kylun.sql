CREATE TABLE `contrib_requests` (
	`id` text PRIMARY KEY NOT NULL,
	`spec` text NOT NULL,
	`status` text NOT NULL,
	`created_at` text NOT NULL
);
--> statement-breakpoint
CREATE TABLE `contrib_skips` (
	`request_id` text NOT NULL,
	`user_id` text NOT NULL,
	PRIMARY KEY(`request_id`, `user_id`)
);
--> statement-breakpoint
CREATE TABLE `contrib_submissions` (
	`id` text PRIMARY KEY NOT NULL,
	`request_id` text NOT NULL,
	`user_id` text NOT NULL,
	`mod_version` text NOT NULL,
	`bytes` integer NOT NULL,
	`created_at` text NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `contrib_submissions_one_per_player` ON `contrib_submissions` (`request_id`,`user_id`);--> statement-breakpoint
CREATE TABLE `contrib_unknowns` (
	`kind` text NOT NULL,
	`signature` text NOT NULL,
	`sample` text NOT NULL,
	`count` integer NOT NULL,
	`mod_version` text NOT NULL,
	`first_seen` text NOT NULL,
	PRIMARY KEY(`kind`, `signature`)
);
