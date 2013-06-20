/*
 * Copyright (C) 2012 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.iscpif.gridscale.ssh

import net.schmizz.sshj._
import net.schmizz.sshj.sftp._
import transport.verification.HostKeyVerifier
import java.security.PublicKey
import fr.iscpif.gridscale.DefaultTimeout

trait SSHHost extends DefaultTimeout {
  type A = SSHAuthentication

  def user: String
  def host: String
  def port: Int = 22

  def withConnection[T](f: SSHClient ⇒ T)(implicit authentication: SSHAuthentication) = {
    val connection = getConnection
    try f(connection)
    finally release(connection)
  }

  def getConnection(implicit authentication: SSHAuthentication) = connect
  def release(c: SSHClient) = c.close

  def connect(implicit authentication: SSHAuthentication) = {
    val ssh = new SSHClient
    ssh.setConnectTimeout(timeout * 1000)
    ssh.setTimeout(timeout * 1000)
    ssh.addHostKeyVerifier(new HostKeyVerifier {
      def verify(p1: String, p2: Int, p3: PublicKey) = true
    })
    ssh.connect(host, port)
    authentication.authenticate(ssh)
    ssh
  }

  def withSftpClient[T](f: SFTPClient ⇒ T)(implicit authentication: SSHAuthentication): T = withConnection {
    connection ⇒
      val sftpClient = connection.newSFTPClient
      try f(sftpClient) finally sftpClient.close
  }
}
