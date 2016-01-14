* High availability with csync2
	http://hswong3i.net/blog/hswong3i/apache2-cluster-csync2-ubuntu-12-04-mini-howto
	http://linuxaria.com/howto/csync2-a-filesystem-syncronization-tool-for-linux

	--Common part to be done on each node--
	$ sudo apt-get install csync2
	(will modify /etc/inetd.conf to add csync2 service)

	If you want csync2 traffic encrypted (optional, you don't have to do this for testing but you have to set 'nossl' in csync2 config, see example later), you need a certificate for each node:
	Generate private key and self-signed cert for secure connections from/to csync2:
	$ sudo openssl genrsa -out /etc/ssl/private/csync2-key.pem 1024
	$ sudo openssl req -new -key /etc/ssl/private/csync2-key.pem -out csync2.csr
	$ sudo openssl x509 -req -days 730 -in csync2.csr -signkey /etc/ssl/private/csync2-key.pem -out /etc/ssl/certs/csync2-cert.pem

	--End of common part--

	--On all the master nodes--
	Authentication is performed using auto-generated pre-shared-keys (in combination with the peer IP address and the peer SSL certificate, so need to generate a secret (shared key):
	$ csync2 -k /etc/csync2.key
	this may take time:
	http://lists.linbit.com/pipermail/csync2/2008-February/000342.html
	***********
	> when I run "csync2 -k /etc/mykey" it just takes forever to complete.
	> In fact it never completes, I  have to CTRL+C to get out and when I
	> cat the file it is half done.
	> 
	> When I do teh same thing on a regular linux pc with the same kernel..
	> it generates the key in less than a second. So I try to use that
	> generated key from the PC onto my blade.. but I get identification
	> errors.

	Key generation uses /dev/random to acquire some random bytes. the random
	data in /dev/random is generated from the kernel using thing like keyboard
	and harddisks interrupts, because they can't be predicted by monitoring the
	network traffic. Servers, especially those with no hard disk, often have 
	problems with not having enough truly random events the kernel could use
	to generate the key.

	Easiest solution: Generate the key on a trustworthy machine where key
	generation works fine, then transfer it to the server using a secure
	channel (such as scp) and remove the key file on the machine where you
	generated it afterwards..
	******

	My comment: if you run the command with strace, you will actually see that it is trying to read bytes from /dev/random, one by one in order to create a 64-bytes base64-encoded string (therefore reading bytes until it has 64 of them) -> 64*64=4096 bit (+ a newline character).

	if you are on a master, say ubuntu199.theresis.org, and master-slave config with ubuntu200.theresis.org as slave...
	Edit /etc/csync2.cfg and update as below (use brackets '()' for slave hosts):
	****
	# please see the README file how to configure csync2
	nossl * *;

	group taz {
	# use bracket for slave hosts
			host    ubuntu199.theresis.org (ubuntu200.theresis.org);
			#pre-shared group authentication key
			key     /etc/csync2.key;
			# Monitored pattern path
			include /home/theresis/;
			# Action when change
			action {
					# For some reason, it can happen that "%%" will list all files changed since first sync after last csync2 daemon started, not only the last changes (during last sync)
					exec "echo %% | tee ${GLASSFISH_DOMAIN_DIR}/logs/authzforce/csync2/lastchange.log | logger -t 'csync2 taz update'";
					# You could also use syslog with bash "logger" command after configuring syslog properly (if not, will go to /var/log/syslog by default): $ echo "local0.* /var/log/csync2.log" > /etc/rsyslog.d/csync2.conf && service rsyslog restart
					# You can also use logfile to log above command output to some custom file
					# logfile "/var/log/csync2.log";
			}
	}
	****

	Make sure local hostnames match names on "host" line and can be DNS-resolved by each other (may need to change /etc/hosts), otherwise csync2 ignores the host.
	Note: the preshared key ("key" line) must be shared with all member hosts mentioned in the group. 

	In this configuration, SSL is disabled. If you want csync2 traffic encrypted using SSL, then comment out the "nossl line"

	Copy all /etc/csync2* files to the other nodes (/etc/ folder) with scp

	--Common part 2--
	On all nodes:
	$ service openbsd-inetd restart
	----
	Try dry-run sync:
	$ csync2 -rxvd
	If you have error "Identification failed", it means the dns lookup for peer hostname as registered in csyn2.cfg does not mach IP address (check your hosts file or DNS)
	When all works, test the actual sync:

	$ csync2 -rxv
	If sth bad happens or you changed the config (esp. directory to sync), do 
	$ csync2 -R
	... to clean the database, before you retry sync

	The lastchangedfiles will look like this (whitespace-separated list of absolute paths): (only changed files during last sync in there, previous list overwritten, no append):
	theresis@sp:~$ cat lastchangedfiles.log
	/home/theresis/csync2testdir/test5.txt /home/theresis/csync2testdir/test4dir/test.txt /home/theresis/csync2testdir/test4dir /home/theresis/csync2testdir/test3.txt

	TODO: test deleted file

	If you use whitespaces in filenames, it will end like this in lastchangefiles.log:
	theresis@sp:~$ cat lastchangedfiles.log
	/home/theresis/csync2testdir/test%20with%20whitespace.txt

	-> URL-encoded (%-encoded)
	
	WHEN all works, create crontab
	Now setup cron jobs with "crontab -e" as below:

*/1 * * * * csync2 -x >/dev/null 2>&1

Once saved, Csync2 will run once per 1 minute, check, synchronize and restart your service if required automatically.
============
	
	-TESTING (showing /var/log/syslog output)-
	1) update domain2/policyset.xml, creating domain5, delete domain 4
	Nov 30 16:28:42 kyrill-old-desktop-pc csync2 taz update: /home/cdangerv/XIFI/domains/domain5/policySet.xml /home/cdangerv/XIFI/domains/domain5/pdp.xml /home/cdangerv/XIFI/domains/domain5 /home/cdangerv/XIFI/domains/domain2/policySet.xml /home/cdangerv/XIFI/domains/domain4 /home/cdangerv/XIFI/domains/domain4/pdp.xml /home/cdangerv/XIFI/domains/domain4/policySet.xml

	2) update domain2/policyset.xml and domain2/pdp.xml
	Nov 30 16:32:09 kyrill-old-desktop-pc csync2 taz update: /home/cdangerv/XIFI/domains/domain2/policySet.xml /home/cdangerv/XIFI/domains/domain2/pdp.xml

	3) delete domain5
	Nov 30 16:33:57 kyrill-old-desktop-pc csync2 update:: /home/cdangerv/XIFI/domains/domain5 /home/cdangerv/XIFI/domains/domain5/pdp.xml /home/cdangerv/XIFI/domains/domain5/policySet.xml

	4) create domain3:
	Nov 30 16:35:17 kyrill-old-desktop-pc csync2 update:: /home/cdangerv/XIFI/domains/domain3/policySet.xml /home/cdangerv/XIFI/domains/domain3/pdp.xml /home/cdangerv/XIFI/domains/domain3
	
		