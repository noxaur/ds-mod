# Sign-in

How a player proves who they are to the backend, from the mod and on the website.

## A player is their Minecraft account, and the client is never believed

The mod is open source, so anyone can build a client that sends anything. So the backend issues a single-use server id, the client tells Mojang it is joining that server, and the backend asks Mojang who joined. Only the holder of an account's session can make that call. A secret in the mod was rejected, because a secret in an open jar is public. This proves who asks, not what they send, which is why a request is recorded by several players and a sighting needs two.

## The website asks Mojang for the backend

Mojang refuses the addresses that Cloudflare Workers share, so a Worker cannot ask who joined. The backend sends the question to a route on the website, which runs on Vercel and asks Mojang for it, with the key the two already share, so nobody else reaches Mojang through it. A proxy of our own was rejected, because it would be a third service to run and pay for. Without the relay set, as in development, the backend asks Mojang itself.

## Only Mojang vouches for an account

Email and password sign-in is off, so no account exists that Mojang did not vouch for. A session ends 24 hours after it began and is never extended, so a stolen token dies by itself. The mod pays nothing for it, because it repeats the proof without the player noticing.

## The website signs in through the mod

The website has no Microsoft login, because one would need an Azure app that Mojang approves. Instead the mod, already signed in, trades its session for a link that works once within a minute, with the token in the link's fragment, which no server receives. The page asks the player to confirm the account before it keeps the session, because anyone can send a player a link to their own account.

## The mod signs in when it first needs to, and keeps the session in memory

Nothing contacts the backend until the player uses a feature that needs it. The session token is held in memory and never written, logged, shown or recorded, because the jar is public, and a token on disk or in a stream is a stolen account for the day it lives. For the same reason the login link opens in the browser and never appears in chat.

## The backend's address is built into the jar

The backend's and the website's addresses are set when the jar is built. They are not a player's setting, because a player who pointed the mod elsewhere would be sending their captures there.
