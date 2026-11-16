# Sign-in

How a player proves who they are to the backend, from the mod and on the website.

## A player is their Minecraft account, and the client is never believed

The mod is open source, so anyone can build a client that sends whatever it likes. Sign-in therefore rests on something a client cannot fake. The backend issues a random server id, the client tells Mojang it is joining that server, and the backend asks Mojang who joined. Only the holder of an account's Minecraft session can make that call, so a modified client can sign in as its own account and as no other. No secret ships in the mod, because a secret in an open jar is public.

The challenge is single-use and is consumed before Mojang is asked, so a replayed one proves nothing.

This proves who is asking and nothing about what they send. A signed-in client can still upload a capture it invented, which is why one request is recorded by several players and an agent compares them, and why an item sighting needs two players. No encryption can close that gap, since the player controls the machine that would hold the key.

Email and password sign-in is switched off, so no account exists that Mojang did not vouch for. A session ends 24 hours after it began and is never extended, so a stolen token dies by itself. The mod pays nothing for that, because it repeats the proof without the player noticing.

The sign-in route is a Better Auth plugin. Its `createAuthEndpoint` takes any Standard Schema for the request body, so the route passes an Effect Schema through `Schema.toStandardSchemaV1` and the backend needs no second schema library. Better Auth brings its own zod, and the backend does not list it, so `import "zod"` does not resolve.

## The website signs in through the mod

The website has no Microsoft login. A site that asked for it would need an Azure app that Mojang approves. Instead the mod, which is already signed in, trades its session for a link that works once within a minute. The token sits in the link's fragment, which a browser sends to no server and leaves out of the Referer, so the website takes it from the page's own script.

The page then asks the player to confirm the account before it keeps the session. Anyone can send a player a link to their own account, and a browser signed in to it unnoticed would hand that person whatever the player does next.

## The mod signs in when it first needs to, and keeps the session in memory

Nothing contacts the backend until the player opens Contribute, uses `/dsmod login`, looks a player up with `/pv`, or has turned on Report Game Data. The session token is held in memory and never written, logged, shown or recorded: the jar is public, and a token on disk or in a stream is a stolen account for the day it lives.

For the same reason `/dsmod login` opens the browser directly, and the login token never appears in chat, where a viewer could use it first. Minecraft's own opener logs the address when it fails, so the mod uses the system's opener and reports a failure without the address.

A session ends after 24 hours, so a 401 signs in again and repeats the call once. A second 401 is an error, not a loop.

## The backend's address is built into the jar

The backend's address and the website's are set when the jar is built, in `gradle.properties`, and written into the jar's metadata. They are not a player's setting, because a player who pointed the mod somewhere else would be sending their captures there. A jar keeps the addresses it was built with, so moving either one strands every player on an older release.
