import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(fs.readFileSync(path.join(directory, 'image-manifest.json'), 'utf8'));
const verified = JSON.parse(fs.readFileSync(path.join(directory, 'image-manifest.json.s3-verification.json'), 'utf8'));
if (manifest.length !== 200 || verified.count !== 200 || verified.bucket !== 'thankunnie-images-dev' ||
    verified.region !== 'ap-southeast-2' || verified.prefix !== 'shop-images/') {
  throw new Error('Expected verified 200 objects in the approved demo bucket');
}
const remote = new Map(verified.objects.map((object) => [object.key, object.sha256]));
const quote = (value) => `'${value.replaceAll("'", "''")}'`;
const begin = '-- BEGIN UNNIE-164 VERIFIED IMAGE LINKS';
const end = '-- END UNNIE-164 VERIFIED IMAGE LINKS';
const sql = [begin,
  '-- S3 저장 및 SHA-256 메타데이터 조회가 성공한 200개 객체만 연결한다.',
  '-- 대상: thankunnie-images-dev / ap-southeast-2 / shop-images/. 버킷은 비공개다.',
  '-- DB에는 기존 백엔드 이미지 API의 상대 경로만 저장한다. AWS 키·S3 공개 URL·만료 URL은 저장하지 않는다.',
  '-- 같은 시술 사진도 슬롯별 UUID 객체로 분리해 한 시술의 교체·삭제가 다른 시술에 영향을 주지 않는다.',
  '-- 기존 이미지가 있는 행은 보존한다. 재실행 시 이미 연결한 행은 변경하지 않는다.',
  '-- 이미지 API에는 현재 DB URL과 정확하게 일치하는 UUID 파일명만 접근할 수 있다.',
];
for (const entry of manifest) {
  if (remote.get(verified.prefix + entry.fileName) !== entry.sha256) throw new Error(`Unverified remote object: ${entry.fileName}`);
  const local = path.join(directory, 'images', `${entry.assetId}.jpg`);
  if (!fs.existsSync(local)) throw new Error(`Missing local image: ${entry.assetId}`);
  if (entry.kind === 'shop') {
    sql.push(`UPDATE SHOP SET IMAGE_URL = '/api/v1/shops/' || TO_CHAR(SHOP_ID) || '/images/${entry.fileName}'
WHERE LOGIN_ID = ${quote(entry.loginId)} AND IMAGE_URL IS NULL;`);
  } else {
    sql.push(`UPDATE TREATMENT SET IMAGE_URL = '/api/v1/shops/' || TO_CHAR(SHOP_ID) || '/treatments/' || TO_CHAR(TREATMENT_ID) || '/images/${entry.fileName}'
WHERE SHOP_ID = (SELECT SHOP_ID FROM SHOP WHERE LOGIN_ID = ${quote(entry.loginId)})
  AND NAME = ${quote(entry.treatmentName)} AND TREATMENT_TYPE = ${quote(entry.treatmentType)} AND IMAGE_URL IS NULL;`);
  }
}
sql.push(end, '');
const block = sql.join('\n');
const target = path.resolve(directory, '../../erd/03_demo_data_dml.sql');
const current = fs.readFileSync(target, 'utf8');
const start = current.indexOf(begin);
const finish = current.indexOf(end);
if ((start < 0) !== (finish < 0)) throw new Error('Incomplete image-link marker; inspect manually');
fs.writeFileSync(target, start < 0 ? `${current.trimEnd()}\n\n${block}` : current.slice(0, start) + block + current.slice(finish + end.length).replace(/^\r?\n/, ''), 'utf8');
const additionsFile = path.join(directory, 'demo164-additions.sql');
const additions = fs.readFileSync(additionsFile, 'utf8');
const previousStart = additions.indexOf(begin);
fs.writeFileSync(additionsFile, `${(previousStart < 0 ? additions : additions.slice(0, previousStart)).trimEnd()}\n\n${block}`, 'utf8');
fs.writeFileSync(path.join(directory, 'image-links.sql'), block, 'utf8');
console.info('Connected 50 shop and 150 treatment image URLs to verified, separately owned S3 objects.');
