#!/usr/bin/perl -w
#
# Este � um script que processa o SPFBL dentro do Postfix.
#
# Aten��o! Para utilizar este servi�o, solicite a libera��o das consultas 
# no servidor 54.94.137.168 atrav�s do endere�o leandro@allchemistry.com.br 
# ou altere o IP 54.94.137.168 deste script para seu servidor SPFBL pr�prio.
#
# Se a mensagem n�o estiver listada, o cabe�alho Received-SPFBL
# ser� adicionado com o resultado do SPFBL.
#
# Para implementar este script no Postfix, 
# adicione as seguintes linhas no arquivo master.cf:
#
#    policy-spfbl  unix  -       n       n       -       -       spawn
#        user=nobody argv=/usr/bin/spfblquery.pl
#

use IO::Socket::INET;

# Captura os atributos do Postfix passados pelo STDIN.
my %attributes;
foreach $line ( <STDIN> ) {
    chomp( $line );
    my ($key, $value) = split(/=/, $line, 2);
    $attributes{$key} = $value;
}

# Associa os par�metros SPFBL atrav�s dos atributos do Postfix.
my $client_address = $attributes{"client_address"};
my $sender = $attributes{"sender"};
my $helo_name = $attributes{"helo_name"};

# auto-flush on socket
$| = 1;

# Create a connecting socket.
my $socket = new IO::Socket::INET (
    PeerHost => '54.94.137.168',
    PeerPort => '9877',
    Proto => 'tcp',
    Timeout => 3
);
die "Can't connect to SPFBL server!\n" unless $socket;

# Data to send to a server.
my $query = "$client_address $sender $helo_name\n";
my $size = $socket->send($query);

# Notify server that request has been sent.
shutdown($socket, 1);
 
# Receive a response of up to 4096 characters from server.
my $result = "";
$socket->recv($result, 4096);
$socket->close();
$result =~ s/\s+$//;

#
# Unbuffer standard output.
#
STDOUT->autoflush(1);

# Sa�da de acordo com documenta��o do Postfix.
if ($result =~ /^LISTED/) {
    STDOUT->print("action=REJECT [RBL] You are blocked in this server for seven days.\n\n");
} elsif ($result =~ /^ERROR: HOST NOT FOUND/) {
    STDOUT->print("action=DEFER [SPF] A transient error occurred when checking SPF record from $sender, preventing a result from being reached. Try again later.\n\n");
} elsif ($result =~ /^ERROR: QUERY/) {
    STDOUT->print("action=DEFER [SPF] A transient error occurred when checking SPF record from $sender, preventing a result from being reached. Try again later.\n\n");
} elsif ($result =~ /^ERROR: /) {
    STDOUT->print("action=REJECT [SPF] One or more SPF records from $sender could not be interpreted. Please see http://www.openspf.org/SPF_Record_Syntax for details.\n\n");
} elsif ($result =~ /^NONE /) {
    STDOUT->print("action=PREPEND Received-SPFBL: $result\n\n");
} elsif ($result =~ /^PASS /) {
    STDOUT->print("action=PREPEND Received-SPFBL: $result\n\n");
} elsif ($result =~ /^FAIL/) {
    STDOUT->print("action=REJECT [SPF] $sender is not allowed to send mail from $client_address. Please see http://www.openspf.org/why.html?sender=$sender&ip=$client_address for details.\n\n");
} elsif ($result =~ /^SOFTFAIL /) {
    STDOUT->print("action=PREPEND Received-SPFBL: $result\n\n");
} elsif ($result =~ /^NEUTRAL /) {
    STDOUT->print("action=PREPEND Received-SPFBL: $result\n\n");
} else {
    STDOUT->print("action=DEFER [SPF] A transient error occurred when checking SPF record from $sender, preventing a result from being reached. Try again later.\n\n");
}

exit 0;
