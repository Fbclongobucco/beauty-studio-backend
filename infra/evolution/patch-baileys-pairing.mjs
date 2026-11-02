// Correção do pareamento de aparelho novo na Evolution API v2.3.7 (Baileys 7.0.0-rc.9).
//
// Desde ~28/07/2026 o WhatsApp envia <notification type="companion_reg_refresh"> durante o pareamento, pedindo
// para trocar o "adv secret" anunciado no QR Code. O Baileys só confirma a notificação e continua mostrando o QR
// com o segredo antigo: o celular lê, diz que não foi possível conectar, e o pareamento nunca termina.
// Porta para a rc.9 a correção proposta no PR #2727 da Evolution API (escrito para a rc14, ainda não incorporado):
//   https://github.com/evolution-foundation/evolution-api/pull/2727
// - o QR passa a ler creds.advSecretKey a cada desenho (não mais uma cópia feita no início);
// - ao receber companion_reg_refresh numa sessão ainda não pareada, gera um segredo novo (32 bytes aleatórios,
//   como o próprio Baileys faz ao criar a sessão), avisa a Evolution (creds.update) e redesenha o QR atual;
// - pareamento por código: ignora link_code_companion_reg sem primary_identity_pub em vez de quebrar.
// Cada trecho original é conferido antes da troca: se a imagem mudar, o build falha em vez de gerar algo quebrado.
import fs from 'node:fs';

const root = '/evolution/node_modules/baileys';
const version = JSON.parse(fs.readFileSync(`${root}/package.json`, 'utf8')).version;
if (version !== '7.0.0-rc.9') {
  throw new Error(`Baileys ${version} inesperado: a correção foi escrita para 7.0.0-rc.9`);
}

function patch(file, pairs) {
  const path = `${root}/lib/${file}`;
  let src = fs.readFileSync(path, 'utf8');
  if (src.includes('beauty-studio: companion_reg_refresh')) {
    console.log(`${file}: já corrigido`);
    return;
  }
  for (const [before, after] of pairs) {
    const count = src.split(before).length - 1;
    if (count !== 1) throw new Error(`${file}: trecho esperado ${count} vez(es) em vez de 1:\n${before}`);
    src = src.replace(before, after);
  }
  fs.writeFileSync(path, src);
  console.log(`${file}: corrigido`);
}

patch('Socket/socket.js', [
  [
    `    // QR gen
    ws.on('CB:iq,type:set,pair-device', async (stanza) => {`,
    `    // beauty-studio: companion_reg_refresh (PR #2727). Redesenha o QR atual com o segredo vigente.
    let renderPairingQR;
    // QR gen
    ws.on('CB:iq,type:set,pair-device', async (stanza) => {`,
  ],
  [
    `        const advB64 = creds.advSecretKey;
        let qrMs = qrTimeout || 60000; // time to let a QR live`,
    `        let qrMs = qrTimeout || 60000; // time to let a QR live`,
  ],
  [
    `            const ref = refNode.content.toString('utf-8');
            const qr = [ref, noiseKeyB64, identityKeyB64, advB64].join(',');
            ev.emit('connection.update', { qr });`,
    `            const ref = refNode.content.toString('utf-8');
            // o segredo é lido a cada desenho: companion_reg_refresh pode trocá-lo no meio do fluxo
            renderPairingQR = () => ev.emit('connection.update', { qr: [ref, noiseKeyB64, identityKeyB64, creds.advSecretKey].join(',') });
            renderPairingQR();`,
  ],
  [
    `    // device paired for the first time
    // if device pairs successfully, the server asks to restart the connection`,
    `    // o servidor aposenta o segredo de um aparelho ainda não pareado: gera outro e redesenha o QR atual
    ws.on('CB:notification,type:companion_reg_refresh', (node) => {
        if (!['companion_reg_refresh', 'pair-device-rotate-qr'].some(tag => getBinaryNodeChild(node, tag))) {
            logger.warn({ node }, 'companion_reg_refresh sem o filho esperado; ignorando');
            return;
        }
        if (creds.me) {
            // sessão já pareada (ou pareamento por código em curso): trocar o segredo quebraria a sessão
            logger.debug({ id: node.attrs.id }, 'companion_reg_refresh em sessão registrada; mantendo o segredo');
            return;
        }
        creds.advSecretKey = randomBytes(32).toString('base64');
        ev.emit('creds.update', { advSecretKey: creds.advSecretKey });
        logger.info({ id: node.attrs.id }, 'segredo do pareamento trocado a pedido do servidor; redesenhando o QR');
        renderPairingQR?.();
    });
    // device paired for the first time
    // if device pairs successfully, the server asks to restart the connection`,
  ],
]);

patch('Socket/messages-recv.js', [
  [
    `            case 'link_code_companion_reg':
                const linkCodeCompanionReg = getBinaryNodeChild(node, 'link_code_companion_reg');`,
    `            case 'link_code_companion_reg':
                const linkCodeCompanionReg = getBinaryNodeChild(node, 'link_code_companion_reg');
                // beauty-studio: companion_reg_refresh (PR #2727) — notificação sem a chave não derruba o pareamento por código
                if (!getBinaryNodeChildBuffer(linkCodeCompanionReg, 'primary_identity_pub')) { break; }`,
  ],
]);
