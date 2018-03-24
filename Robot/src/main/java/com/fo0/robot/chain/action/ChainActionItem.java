package com.fo0.robot.chain.action;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.rauschig.jarchivelib.ArchiveFormat;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.rauschig.jarchivelib.CompressionType;

import com.fo0.robot.chain.ChainCommand;
import com.fo0.robot.chain.EChainResponse;
import com.fo0.robot.commander.Commander;
import com.fo0.robot.connector.FTPClient;
import com.fo0.robot.connector.SCPClient;
import com.fo0.robot.connector.SimpleSSHClient;
import com.fo0.robot.enums.EActionType;
import com.fo0.robot.model.ActionItem;
import com.fo0.robot.model.FileTransferData;
import com.fo0.robot.model.Host;
import com.fo0.robot.model.KeyValue;
import com.fo0.robot.utils.CONSTANTS;
import com.fo0.robot.utils.Logger;
import com.fo0.robot.utils.Utils;
import com.google.common.base.Stopwatch;

import lombok.Builder;

@Builder
public class ChainActionItem implements ChainCommand<ActionContext> {

	private ActionContext ctx;
	private EActionType type;

	@Override
	public EChainResponse command(ActionContext ctx) throws Exception {
		// get latest action
		this.ctx = ctx;
		Entry<Integer, ActionItem> item = ctx.pop();

		// info
		Logger.info("Action[" + item.getKey() + "] " + item.getValue());
		type = item.getValue().getType();
		switch (type) {
		case Commandline:
			Commander commander = new Commander(log -> {
				ctx.addToLogPlain(type, log);
			});

			commander.execute(true, System.getProperty("user.dir"), item.getValue().getValue());

			if (commander == null || commander.isError()) {
				ctx.addToLog(type, "error at commander: " + item.getKey());
				return EChainResponse.Failed;
			}
			break;

		case Download:
			download(item.getValue().parsedValue());
			break;

		case Zip:
			zip(item.getValue().parsedValue());
			break;

		case Unzip:
			unzip(item.getValue().parsedValue());
			break;

		case TAR:
			tar(item.getValue().parsedValue());
			break;

		case UNTAR:
			untar(item.getValue().parsedValue());
			break;

		case TARGZ:
			targz(item.getValue().parsedValue());
			break;

		case UNTARGZ:
			untargz(item.getValue().parsedValue());
			break;

		case SEVEN_ZIP:
			sevenZip(item.getValue().parsedValue());
			break;

		case UNSEVEN_ZIP:
			unSevenZip(item.getValue().parsedValue());
			break;

		case SSH:
			ssh(item.getValue().parsedValue());
			break;

		case SCP_Download:
		case SCP_Upload:
			scp(item.getValue().parsedValue());
			break;

		case FTP_Download:
			ftp(item.getValue().parsedValue());
			break;

		default:
			ctx.addToLog(type, "Currently not implemented, you may check for updates");
			break;
		}

		return EChainResponse.Continue;
	}

	public EChainResponse download(List<KeyValue> list) throws Exception {
		List<KeyValue> downloads = list;
		KeyValue url = downloads.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst().orElse(null);
		KeyValue path = downloads.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst().orElse(
				KeyValue.builder().key(CONSTANTS.DESTINATION).value(FilenameUtils.getName(url.getValue())).build());

		ctx.addToLog(type, "SRC: " + url);
		ctx.addToLog(type, "DST: " + path);

		// create file
		File file = new File(Paths.get(path.getValue()).toAbsolutePath().toString());

		if (file.exists()) {
			file.delete();
		}

		file.createNewFile();
		Stopwatch timer = Stopwatch.createStarted();
		try {
			FileUtils.copyInputStreamToFile(new URL(url.getValue()).openStream(), file);
			timer.stop();
			ctx.addToLog(type,
					"Finished Download: " + file.getName() + ", Size: "
							+ FileUtils.byteCountToDisplaySize(FileUtils.sizeOf(file)) + ", Speed: "
							+ Utils.humanReadableBandwith(timer.elapsed(TimeUnit.MILLISECONDS), file.length()));
		} catch (Exception e2) {
			ctx.addToLog(type, "failed to download: " + url.getValue());
		} finally {
			try {
				timer.stop();
			} catch (Exception e3) {
			}
		}
		return EChainResponse.Continue;
	}

	public EChainResponse ssh(List<KeyValue> list) throws Exception {
		List<KeyValue> sshList = list;
		KeyValue sshHost = sshList.stream().filter(e -> e.getKey().equals(CONSTANTS.HOST)).findFirst().orElse(null);
		KeyValue sshPort = sshList.stream().filter(e -> e.getKey().equals(CONSTANTS.PORT)).findFirst()
				.orElse(KeyValue.builder().key("$PORT").value("22").build());
		KeyValue sshUser = sshList.stream().filter(e -> e.getKey().equals(CONSTANTS.USER)).findFirst().orElse(null);
		KeyValue sshPassword = sshList.stream().filter(e -> e.getKey().equals(CONSTANTS.PASSWORD)).findFirst()
				.orElse(KeyValue.builder().key(CONSTANTS.PASSWORD).value("").build());
		KeyValue sshCmd = sshList.stream().filter(e -> e.getKey().equals(CONSTANTS.CMD)).findFirst().orElse(null);

		ctx.addToLog(type, "HOST: " + sshHost.getValue());
		ctx.addToLog(type, "PORT: " + sshPort.getValue());
		ctx.addToLog(type, "User: " + sshUser.getValue());
		ctx.addToLog(type, "Password: " + StringUtils
				.join(IntStream.range(0, sshPassword.getValue().length()).mapToObj(e -> "*").toArray(String[]::new)));
		ctx.addToLog(type, "CMD: " + sshCmd.getValue());

		SimpleSSHClient sshClient = new SimpleSSHClient(
				Host.builder().address(sshHost.getValue()).port(Integer.parseInt(sshPort.getValue()))
						.username(sshUser.getValue()).password(sshPassword.getValue()).build());

		try {
			sshClient.connect();
		} catch (Exception e2) {
			ctx.addToLog(type, "failed to connect to Host " + e2);
			return EChainResponse.Failed;
		}

		sshClient.command(sshCmd.getValue(), out -> {
			ctx.addToLogPlain(type, out);
		}, error -> {
			ctx.addToLogPlain(type, error);
		});

		return EChainResponse.Continue;
	}

	public EChainResponse zip(List<KeyValue> list) throws Exception {
		List<KeyValue> zipList = list;
		KeyValue zipSrc = zipList.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst().orElse(null);
		KeyValue zipDest = zipList.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst()
				.orElse(KeyValue.builder().build());

		ctx.addToLog(type, "SRC: " + zipSrc.getValue());
		ctx.addToLog(type, "DST: " + zipDest.getValue());

		// old method
		// ZipUtils.zip(zipSrc.getValue(), zipDest.getValue());
		Archiver zipArchive = ArchiverFactory.createArchiver(ArchiveFormat.ZIP);
		zipArchive.create(new File(zipDest.getValue()).getName(), new File(zipDest.getValue()).getParentFile(),
				new File(zipSrc.getValue()));
		return EChainResponse.Continue;
	}

	public EChainResponse unzip(List<KeyValue> list) throws Exception {
		List<KeyValue> unzipList = list;
		KeyValue unzipSrc = unzipList.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst()
				.orElse(null);
		KeyValue unzipDst = unzipList.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst()
				.orElse(KeyValue.builder().build());

		ctx.addToLog(type, "SRC: " + unzipSrc.getValue());
		ctx.addToLog(type, "DST: " + unzipDst.getValue());

		Archiver unzipArchive = ArchiverFactory.createArchiver(ArchiveFormat.ZIP);
		unzipArchive.extract(new File(unzipSrc.getValue()), new File(unzipDst.getValue()));
		return EChainResponse.Continue;
	}

	public EChainResponse scp(List<KeyValue> list) throws Exception {
		List<KeyValue> scpList = list;
		KeyValue scpHost = scpList.stream().filter(e -> e.getKey().equals(CONSTANTS.HOST)).findFirst().orElse(null);
		KeyValue scpPort = scpList.stream().filter(e -> e.getKey().equals(CONSTANTS.PORT)).findFirst()
				.orElse(KeyValue.builder().key(CONSTANTS.PORT).value("22").build());
		KeyValue scpUser = scpList.stream().filter(e -> e.getKey().equals(CONSTANTS.USER)).findFirst().orElse(null);
		KeyValue scpPassword = scpList.stream().filter(e -> e.getKey().equals(CONSTANTS.PASSWORD)).findFirst()
				.orElse(KeyValue.builder().key(CONSTANTS.PASSWORD).value("").build());
		KeyValue scpSrc = scpList.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst().orElse(null);
		KeyValue scpDst = scpList.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst()
				.orElse(null);

		ctx.addToLog(type, "HOST: " + scpHost.getValue());
		ctx.addToLog(type, "PORT: " + scpPort.getValue());
		ctx.addToLog(type, "User: " + scpUser.getValue());
		ctx.addToLog(type, "Password: " + StringUtils
				.join(IntStream.range(0, scpPassword.getValue().length()).mapToObj(e -> "*").toArray(String[]::new)));
		ctx.addToLog(type, "SRC: " + scpSrc.getValue());
		ctx.addToLog(type, "DST: " + scpDst.getValue());

		SCPClient scpClient = new SCPClient(
				Host.builder().address(scpHost.getValue()).port(Integer.parseInt(scpPort.getValue()))
						.username(scpUser.getValue()).password(scpPassword.getValue()).build());
		try {
			scpClient.connect();
		} catch (Exception e2) {
			ctx.addToLog(type, "failed to connect to Host " + e2);
			return EChainResponse.Failed;
		}

		FileTransferData data = null;

		// establish transfer
		try {
			if (type == EActionType.SCP_Download) {
				data = scpClient.download(scpDst.getValue(), scpSrc.getValue());
			} else {
				data = scpClient.upload(scpDst.getValue(), scpSrc.getValue());
			}
		} catch (Exception e2) {
			ctx.addToLog(type, "failed to transfer data " + e2);
			data = FileTransferData.builder().build();
		}

		ctx.addToLog(type, "Transfer: " + data.info());
		return EChainResponse.Continue;
	}

	public EChainResponse ftp(List<KeyValue> list) throws Exception {
		List<KeyValue> ftpList = list;
		KeyValue ftpHost = ftpList.stream().filter(e -> e.getKey().equals(CONSTANTS.HOST)).findFirst().orElse(null);
		KeyValue ftpPort = ftpList.stream().filter(e -> e.getKey().equals(CONSTANTS.PORT)).findFirst()
				.orElse(KeyValue.builder().key("PORT").value("21").build());
		KeyValue ftpUser = ftpList.stream().filter(e -> e.getKey().equals(CONSTANTS.USER)).findFirst()
				.orElse(KeyValue.builder().key("USER").value("anonymous").build());
		KeyValue ftpPassword = ftpList.stream().filter(e -> e.getKey().equals(CONSTANTS.PASSWORD)).findFirst()
				.orElse(KeyValue.builder().key("PASSWORD").value("").build());
		KeyValue ftpSrc = ftpList.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst().orElse(null);
		KeyValue ftpDst = ftpList.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst()
				.orElse(null);

		ctx.addToLog(type, "HOST: " + ftpHost.getValue());
		ctx.addToLog(type, "PORT: " + ftpPort.getValue());
		ctx.addToLog(type, "User: " + ftpUser.getValue());
		ctx.addToLog(type, "Password: " + StringUtils
				.join(IntStream.range(0, ftpPassword.getValue().length()).mapToObj(e -> "*").toArray(String[]::new)));
		ctx.addToLog(type, "SRC: " + ftpSrc.getValue());
		ctx.addToLog(type, "DST: " + ftpDst.getValue());

		FTPClient ftpClient = new FTPClient(
				Host.builder().address(ftpHost.getValue()).port(Integer.parseInt(ftpPort.getValue()))
						.username(ftpUser.getValue()).password(ftpPassword.getValue()).build());

		if (!ftpClient.connect()) {
			ctx.addToLog(type, "failed to connect to Host");
			return EChainResponse.Failed;
		}

		FileTransferData ftpData = ftpClient.download(ftpDst.getValue(), ftpSrc.getValue());
		try {
			ctx.addToLog(type, "Transfer: " + ftpData.info());
		} catch (Exception e2) {
			e2.printStackTrace();
		}
		return EChainResponse.Continue;
	}

	public EChainResponse tar(List<KeyValue> list) throws Exception {
		List<KeyValue> zipList = list;
		KeyValue zipSrc = zipList.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst().orElse(null);
		KeyValue zipDest = zipList.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst()
				.orElse(KeyValue.builder().build());

		ctx.addToLog(type, "SRC: " + zipSrc.getValue());
		ctx.addToLog(type, "DST: " + zipDest.getValue());

		// old method
		// ZipUtils.zip(zipSrc.getValue(), zipDest.getValue());
		Archiver zipArchive = ArchiverFactory.createArchiver(ArchiveFormat.TAR);
		zipArchive.create(new File(zipDest.getValue()).getName(), new File(zipDest.getValue()).getParentFile(),
				new File(zipSrc.getValue()));
		return EChainResponse.Continue;
	}

	public EChainResponse untar(List<KeyValue> list) throws Exception {
		List<KeyValue> unzipList = list;
		KeyValue unzipSrc = unzipList.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst()
				.orElse(null);
		KeyValue unzipDst = unzipList.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst()
				.orElse(KeyValue.builder().build());

		ctx.addToLog(type, "SRC: " + unzipSrc.getValue());
		ctx.addToLog(type, "DST: " + unzipDst.getValue());

		Archiver unzipArchive = ArchiverFactory.createArchiver(ArchiveFormat.TAR);
		unzipArchive.extract(new File(unzipSrc.getValue()), new File(unzipDst.getValue()));
		return EChainResponse.Continue;
	}

	public EChainResponse targz(List<KeyValue> list) throws Exception {
		List<KeyValue> zipList = list;
		KeyValue zipSrc = zipList.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst().orElse(null);
		KeyValue zipDest = zipList.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst()
				.orElse(KeyValue.builder().build());

		ctx.addToLog(type, "SRC: " + zipSrc.getValue());
		ctx.addToLog(type, "DST: " + zipDest.getValue());

		// old method
		// ZipUtils.zip(zipSrc.getValue(), zipDest.getValue());
		Archiver zipArchive = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP);
		zipArchive.create(new File(zipDest.getValue()).getName(), new File(zipDest.getValue()).getParentFile(),
				new File(zipSrc.getValue()));
		return EChainResponse.Continue;
	}

	public EChainResponse untargz(List<KeyValue> list) throws Exception {
		List<KeyValue> unzipList = list;
		KeyValue unzipSrc = unzipList.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst()
				.orElse(null);
		KeyValue unzipDst = unzipList.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst()
				.orElse(KeyValue.builder().build());

		ctx.addToLog(type, "SRC: " + unzipSrc.getValue());
		ctx.addToLog(type, "DST: " + unzipDst.getValue());

		Archiver unzipArchive = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP);
		unzipArchive.extract(new File(unzipSrc.getValue()), new File(unzipDst.getValue()));
		return EChainResponse.Continue;
	}

	public EChainResponse sevenZip(List<KeyValue> list) throws Exception {
		List<KeyValue> zipList = list;
		KeyValue zipSrc = zipList.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst().orElse(null);
		KeyValue zipDest = zipList.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst()
				.orElse(KeyValue.builder().build());

		ctx.addToLog(type, "SRC: " + zipSrc.getValue());
		ctx.addToLog(type, "DST: " + zipDest.getValue());

		// old method
		// ZipUtils.zip(zipSrc.getValue(), zipDest.getValue());
		Archiver zipArchive = ArchiverFactory.createArchiver(ArchiveFormat.SEVEN_Z);
		zipArchive.create(new File(zipDest.getValue()).getName(), new File(zipDest.getValue()).getParentFile(),
				new File(zipSrc.getValue()));
		return EChainResponse.Continue;
	}

	public EChainResponse unSevenZip(List<KeyValue> list) throws Exception {
		List<KeyValue> unzipList = list;
		KeyValue unzipSrc = unzipList.stream().filter(e -> e.getKey().equals(CONSTANTS.SOURCE)).findFirst()
				.orElse(null);
		KeyValue unzipDst = unzipList.stream().filter(e -> e.getKey().equals(CONSTANTS.DESTINATION)).findFirst()
				.orElse(KeyValue.builder().build());

		ctx.addToLog(type, "SRC: " + unzipSrc.getValue());
		ctx.addToLog(type, "DST: " + unzipDst.getValue());

		Archiver unzipArchive = ArchiverFactory.createArchiver(ArchiveFormat.SEVEN_Z);
		unzipArchive.extract(new File(unzipSrc.getValue()), new File(unzipDst.getValue()));
		return EChainResponse.Continue;
	}

}
