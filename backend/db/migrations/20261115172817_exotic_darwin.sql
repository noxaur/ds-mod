CREATE TABLE `item_samples` (
	`entry` text NOT NULL,
	`user_id` text NOT NULL,
	`sample` text NOT NULL,
	PRIMARY KEY(`entry`, `user_id`)
);
--> statement-breakpoint
CREATE TABLE `item_seen` (
	`entry` text NOT NULL,
	`user_id` text NOT NULL,
	`glint` integer NOT NULL,
	`plain` integer NOT NULL,
	`mod_version` text NOT NULL,
	PRIMARY KEY(`entry`, `user_id`)
);
--> statement-breakpoint
CREATE TABLE `item_textures` (
	`entry` text NOT NULL,
	`user_id` text NOT NULL,
	`skin_id` text NOT NULL,
	`texture` text NOT NULL,
	PRIMARY KEY(`entry`, `user_id`, `skin_id`)
);
